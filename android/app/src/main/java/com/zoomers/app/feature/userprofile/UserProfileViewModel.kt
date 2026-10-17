package com.zoomers.app.feature.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.UserRepository
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.PublicProfileUi
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileUiState(
    val loading: Boolean = true,
    val profile: PublicProfileUi? = null,
    val posts: List<PostUi> = emptyList(),
    val followBusy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    fun load(username: String) {
        _state.update { it.copy(loading = it.profile == null, error = null) }
        viewModelScope.launch {
            when (val r = userRepository.profile(username)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, profile = r.data) }
                    if (r.data.canViewPosts) loadPosts(r.data.id)
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun loadPosts(userId: String) {
        viewModelScope.launch {
            when (val r = userRepository.posts(userId)) {
                is ApiResult.Success -> _state.update { it.copy(posts = r.data.items) }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun toggleFollow() {
        val profile = _state.value.profile ?: return
        if (_state.value.followBusy || profile.isSelf) return

        _state.update { it.copy(followBusy = true) }
        viewModelScope.launch {
            if (profile.isFollowing || profile.hasOutgoingRequest) {
                when (userRepository.unfollow(profile.id)) {
                    is ApiResult.Success -> updateProfile {
                        it.copy(
                            isFollowing = false,
                            hasOutgoingRequest = false,
                            followerCount = (it.followerCount - if (profile.isFollowing) 1 else 0).coerceAtLeast(0),
                        )
                    }
                    is ApiResult.Error -> Unit
                }
                _state.update { it.copy(followBusy = false) }
            } else {
                when (val r = userRepository.follow(profile.id)) {
                    is ApiResult.Success -> {
                        val requested = r.data == 1 // Requested (private account)
                        updateProfile {
                            it.copy(
                                isFollowing = !requested,
                                hasOutgoingRequest = requested,
                                followerCount = it.followerCount + if (!requested) 1 else 0,
                            )
                        }
                    }
                    is ApiResult.Error -> Unit
                }
                _state.update { it.copy(followBusy = false) }
            }
        }
    }

    private fun updateProfile(transform: (PublicProfileUi) -> PublicProfileUi) {
        _state.update { s -> s.copy(profile = s.profile?.let(transform)) }
    }
}
