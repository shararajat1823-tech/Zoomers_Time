package com.zoomers.app.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.ProfileRepository
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedPostsUiState(
    val loading: Boolean = true,
    val posts: List<PostUi> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SavedPostsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SavedPostsUiState())
    val state: StateFlow<SavedPostsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.posts.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val r = profileRepository.savedPosts()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, posts = r.data.items) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
