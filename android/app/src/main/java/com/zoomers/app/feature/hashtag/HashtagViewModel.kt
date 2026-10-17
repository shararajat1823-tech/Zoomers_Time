package com.zoomers.app.feature.hashtag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.UserRepository
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HashtagUiState(
    val loading: Boolean = true,
    val posts: List<PostUi> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HashtagViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HashtagUiState())
    val state: StateFlow<HashtagUiState> = _state.asStateFlow()

    fun load(tag: String) {
        _state.update { it.copy(loading = it.posts.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val r = userRepository.hashtagPosts(tag)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, posts = r.data.items) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
