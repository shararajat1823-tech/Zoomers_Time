package com.zoomers.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoomers.app.core.data.AuthRepository
import com.zoomers.app.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(error = null) }

    fun login(usernameOrEmail: String, password: String) {
        if (!validate(usernameOrEmail.isNotBlank() && password.isNotBlank())) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.login(usernameOrEmail, password)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun register(username: String, email: String, password: String, displayName: String) {
        if (!validate(username.isNotBlank() && password.length >= 8 && displayName.isNotBlank())) {
            _state.update { it.copy(error = "Please fill every field. Passwords need 8+ characters.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.register(username, email, password, displayName)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun validate(ok: Boolean): Boolean {
        if (!ok) _state.update { it.copy(error = "Please fill in the required fields.") }
        return ok
    }
}
