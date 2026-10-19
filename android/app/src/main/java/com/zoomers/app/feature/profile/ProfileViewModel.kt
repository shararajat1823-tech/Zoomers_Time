package com.zoomers.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.AuthRepository
import com.zoomers.app.core.data.FeedRefreshBus
import com.zoomers.app.core.data.ProfileRepository
import com.zoomers.app.core.data.ReelRepository
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.ProfileUi
import com.zoomers.app.core.model.ReelUi
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

/** The tabs on the signed-in user's profile. */
enum class ProfileTab { Grid, Reels, Saved }

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: ProfileUi? = null,
    val posts: List<PostUi> = emptyList(),
    val tab: ProfileTab = ProfileTab.Grid,
    val reels: List<ReelUi> = emptyList(),
    val reelsLoading: Boolean = false,
    val saved: List<PostUi> = emptyList(),
    val savedLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val reelRepository: ReelRepository,
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

    fun selectTab(tab: ProfileTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab) }
        when (tab) {
            ProfileTab.Reels -> if (_state.value.reels.isEmpty()) loadReels()
            ProfileTab.Saved -> if (_state.value.saved.isEmpty()) loadSaved()
            ProfileTab.Grid -> Unit
        }
    }

    private fun loadReels() {
        val userId = _state.value.profile?.id ?: return
        _state.update { it.copy(reelsLoading = true) }
        viewModelScope.launch {
            val items = (reelRepository.userReels(userId) as? ApiResult.Success)?.data?.items.orEmpty()
            _state.update { it.copy(reelsLoading = false, reels = items) }
        }
    }

    private fun loadSaved() {
        _state.update { it.copy(savedLoading = true) }
        viewModelScope.launch {
            val items = (profileRepository.savedPosts() as? ApiResult.Success)?.data?.items.orEmpty()
            _state.update { it.copy(savedLoading = false, saved = items) }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
