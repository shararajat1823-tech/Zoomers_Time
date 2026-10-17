package com.zoomers.app.feature.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.NewPostImage
import com.zoomers.app.core.data.PostRepository
import com.zoomers.app.core.media.ImageCompressor
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Post visibility, matching the backend enum (0 public, 1 followers, 2 private). */
enum class PostAudience(val apiValue: Int, val label: String) {
    Public(0, "Public"),
    Followers(1, "Followers"),
    Private(2, "Only me"),
}

/** Up to this many photos per post (matches the backend carousel limit). */
const val MaxPostImages = 10

data class CreatePostUiState(
    val imageUris: List<Uri> = emptyList(),
    val caption: String = "",
    val audience: PostAudience = PostAudience.Public,
    val submitting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false,
) {
    val canShare: Boolean get() = imageUris.isNotEmpty() && !submitting
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postRepository: PostRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreatePostUiState())
    val state: StateFlow<CreatePostUiState> = _state.asStateFlow()

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            _state.update { it.copy(imageUris = uris.take(MaxPostImages), error = null) }
        }
    }

    fun onCaptionChange(value: String) = _state.update { it.copy(caption = value) }

    fun onAudienceChange(audience: PostAudience) = _state.update { it.copy(audience = audience) }

    fun clearImages() = _state.update { it.copy(imageUris = emptyList()) }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun share() {
        val current = _state.value
        if (current.imageUris.isEmpty() || current.submitting) return

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val images = ArrayList<NewPostImage>(current.imageUris.size)
            for (uri in current.imageUris) {
                val image = ImageCompressor.fromUri(context, uri)
                if (image == null) {
                    _state.update { it.copy(submitting = false, error = "Couldn't read one of the images. Try different ones.") }
                    return@launch
                }
                images += image
            }
            when (val r = postRepository.createImagesPost(images, current.caption, current.audience.apiValue)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, posted = true) }
                is ApiResult.Error -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }
}
