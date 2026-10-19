package com.zoomers.app.feature.reels

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.NewPostImage
import com.zoomers.app.core.data.ReelRepository
import com.zoomers.app.core.data.UserRepository
import com.zoomers.app.core.model.ReelUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReelsUiState(
    val loading: Boolean = true,
    val reels: List<ReelUi> = emptyList(),
    val nextCursor: String? = null,
    val uploading: Boolean = false,
    val error: String? = null,
    /** Author ids the viewer followed during this session (hides the Follow pill). */
    val followedAuthors: Set<String> = emptySet(),
)

@HiltViewModel
class ReelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reelRepository: ReelRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReelsUiState())
    val state: StateFlow<ReelsUiState> = _state.asStateFlow()

    private var loadingMore = false

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = it.reels.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val r = reelRepository.feed(cursor = null)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, reels = r.data.items, nextCursor = r.data.nextCursor) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            when (val r = reelRepository.feed(cursor = cursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(reels = it.reels + r.data.items, nextCursor = r.data.nextCursor)
                }
                is ApiResult.Error -> { /* keep what we have */ }
            }
            loadingMore = false
        }
    }

    fun toggleLike(reel: ReelUi) {
        val liked = !reel.viewerHasLiked
        // Optimistic update.
        _state.update { s ->
            s.copy(reels = s.reels.map {
                if (it.id == reel.id) it.copy(
                    viewerHasLiked = liked,
                    likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
                ) else it
            })
        }
        viewModelScope.launch {
            val r = if (liked) reelRepository.like(reel.id) else reelRepository.unlike(reel.id)
            if (r is ApiResult.Success) {
                _state.update { s ->
                    s.copy(reels = s.reels.map { if (it.id == reel.id) it.copy(likeCount = r.data) else it })
                }
            }
        }
    }

    fun follow(authorId: String) {
        if (authorId in _state.value.followedAuthors) return
        _state.update { it.copy(followedAuthors = it.followedAuthors + authorId) }
        viewModelScope.launch {
            if (userRepository.follow(authorId) is ApiResult.Error) {
                // Revert if the server rejected it (e.g. private account pending request stays hidden).
                _state.update { it.copy(followedAuthors = it.followedAuthors - authorId) }
            }
        }
    }

    fun addReel(uri: Uri?, caption: String? = null) {
        if (uri == null) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val probe = withContext(Dispatchers.IO) { readVideo(uri) }
            if (probe == null) {
                _state.update { it.copy(uploading = false, error = "Couldn't read that video, or it's too large (max 100 MB).") }
                return@launch
            }
            when (val r = reelRepository.create(probe.video, probe.thumbnail, caption, probe.durationMs)) {
                is ApiResult.Success -> _state.update {
                    it.copy(uploading = false, reels = listOf(r.data) + it.reels)
                }
                is ApiResult.Error -> _state.update { it.copy(uploading = false, error = r.message) }
            }
        }
    }

    private data class Probe(val video: NewPostImage, val durationMs: Int, val thumbnail: NewPostImage?)

    private fun readVideo(uri: Uri): Probe? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty() || bytes.size > MaxVideoBytes) return null

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) { val t = w; w = h; h = t }
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0

                // Best-effort poster frame (first keyframe) for grids/first paint.
                val thumbnail = runCatching {
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { bmp ->
                        val out = java.io.ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        NewPostImage(bytes = out.toByteArray(), contentType = "image/jpeg", width = bmp.width, height = bmp.height)
                    }
                }.getOrNull()

                Probe(NewPostImage(bytes = bytes, contentType = "video/mp4", width = w, height = h), duration, thumbnail)
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MaxVideoBytes = 100 * 1024 * 1024 // 100 MB client cap
    }
}
