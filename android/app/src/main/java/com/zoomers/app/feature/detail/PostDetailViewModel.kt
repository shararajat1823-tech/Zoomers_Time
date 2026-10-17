package com.zoomers.app.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.FeedRepository
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostDetailUiState(
    val loading: Boolean = true,
    val post: PostUi? = null,
    val error: String? = null,
)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PostDetailUiState())
    val state: StateFlow<PostDetailUiState> = _state.asStateFlow()

    private var postId: String? = null

    fun load(postId: String) {
        this.postId = postId
        _state.update { it.copy(loading = it.post == null, error = null) }
        viewModelScope.launch {
            when (val r = feedRepository.post(postId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, post = r.data) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun toggleLike(post: PostUi) {
        val liked = !post.hasLiked
        updatePost { it.copy(hasLiked = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            val r = if (liked) feedRepository.like(post.id) else feedRepository.unlike(post.id)
            when (r) {
                is ApiResult.Success -> updatePost { it.copy(likeCount = r.data) }
                is ApiResult.Error -> updatePost { it.copy(hasLiked = post.hasLiked, likeCount = post.likeCount) }
            }
        }
    }

    fun toggleSave(post: PostUi) {
        val saved = !post.hasSaved
        updatePost { it.copy(hasSaved = saved) }
        viewModelScope.launch {
            val r = if (saved) feedRepository.save(post.id) else feedRepository.unsave(post.id)
            if (r is ApiResult.Error) updatePost { it.copy(hasSaved = post.hasSaved) }
        }
    }

    private fun updatePost(transform: (PostUi) -> PostUi) {
        _state.update { s -> s.copy(post = s.post?.let(transform)) }
    }
}
