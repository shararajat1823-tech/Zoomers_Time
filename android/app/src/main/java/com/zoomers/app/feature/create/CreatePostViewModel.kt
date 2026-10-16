package com.zoomers.app.feature.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class CreatePostUiState(
    val imageUri: Uri? = null,
    val caption: String = "",
    val audience: PostAudience = PostAudience.Public,
    val submitting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false,
) {
    val canShare: Boolean get() = imageUri != null && !submitting
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postRepository: PostRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreatePostUiState())
    val state: StateFlow<CreatePostUiState> = _state.asStateFlow()

    fun onImagePicked(uri: Uri?) {
        if (uri != null) _state.update { it.copy(imageUri = uri, error = null) }
    }

    fun onCaptionChange(value: String) = _state.update { it.copy(caption = value) }

    fun onAudienceChange(audience: PostAudience) = _state.update { it.copy(audience = audience) }

    fun clearImage() = _state.update { it.copy(imageUri = null) }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun share() {
        val current = _state.value
        val uri = current.imageUri ?: return
        if (current.submitting) return

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val image = ImageCompressor.fromUri(context, uri)
            if (image == null) {
                _state.update { it.copy(submitting = false, error = "Couldn't read that image. Try a different one.") }
                return@launch
            }
            when (val r = postRepository.createImagePost(image, current.caption, current.audience.apiValue)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, posted = true) }
                is ApiResult.Error -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }
}
