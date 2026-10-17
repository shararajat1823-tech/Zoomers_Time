package com.zoomers.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.AuthRepository
import com.zoomers.app.core.data.ProfileRepository
import com.zoomers.app.core.data.SettingsRepository
import com.zoomers.app.core.model.SettingsUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.dto.UpdateSettingsRequestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val isPrivate: Boolean = false,
    val settings: SettingsUi? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = settingsRepository.get()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, settings = r.data) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
        viewModelScope.launch {
            when (val r = profileRepository.me()) {
                is ApiResult.Success -> _state.update { it.copy(isPrivate = r.data.isPrivate) }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun setPrivate(value: Boolean) {
        _state.update { it.copy(isPrivate = value) }
        viewModelScope.launch {
            if (profileRepository.setPrivate(value) is ApiResult.Error) {
                _state.update { it.copy(isPrivate = !value) } // rollback
            }
        }
    }

    fun setOnlineStatus(value: Boolean) = patch(UpdateSettingsRequestDto(showOnlineStatus = value)) { it.copy(showOnlineStatus = value) }
    fun setPushLikes(value: Boolean) = patch(UpdateSettingsRequestDto(pushLikes = value)) { it.copy(pushLikes = value) }
    fun setPushComments(value: Boolean) = patch(UpdateSettingsRequestDto(pushComments = value)) { it.copy(pushComments = value) }
    fun setPushFollows(value: Boolean) = patch(UpdateSettingsRequestDto(pushFollows = value)) { it.copy(pushFollows = value) }

    private fun patch(body: UpdateSettingsRequestDto, optimistic: (SettingsUi) -> SettingsUi) {
        val current = _state.value.settings ?: return
        _state.update { it.copy(settings = optimistic(current)) }
        viewModelScope.launch {
            if (settingsRepository.update(body) is ApiResult.Error) {
                _state.update { it.copy(settings = current) } // rollback
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
