package com.zoomers.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.AuthRepository
import com.zoomers.app.core.data.FeedRefreshBus
import com.zoomers.app.core.data.ProfileRepository
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.ProfileUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: ProfileUi? = null,
    val posts: List<PostUi> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    feedRefreshBus: FeedRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
        // Reload the grid when the user publishes a new post.
        feedRefreshBus.events.onEach { load() }.launchIn(viewModelScope)
    }

    fun load() {
        _state.update { it.copy(loading = it.profile == null, error = null) }
        viewModelScope.launch {
            when (val r = profileRepository.me()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, profile = r.data) }
                    loadPosts(r.data.id)
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun loadPosts(userId: String) {
        viewModelScope.launch {
            when (val r = profileRepository.userPosts(userId)) {
                is ApiResult.Success -> _state.update { it.copy(posts = r.data.items) }
                is ApiResult.Error -> Unit // keep profile even if the grid fails
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
