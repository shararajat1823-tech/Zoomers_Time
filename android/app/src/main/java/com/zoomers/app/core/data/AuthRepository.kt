package com.zoomers.app.core.data

import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.LoginRequestDto
import com.zoomers.app.core.network.dto.LogoutRequestDto
import com.zoomers.app.core.network.dto.RegisterRequestDto
import com.zoomers.app.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ZoomersApi,
    private val tokenStore: TokenStore,
) {
    val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedInFlow

    suspend fun login(usernameOrEmail: String, password: String): ApiResult<Unit> =
        when (val r = safeApiCall { api.login(LoginRequestDto(usernameOrEmail.trim(), password)) }) {
            is ApiResult.Success -> {
                tokenStore.save(r.data.accessToken, r.data.refreshToken)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> r
        }

    suspend fun register(username: String, email: String?, password: String, displayName: String): ApiResult<Unit> =
        when (val r = safeApiCall { api.register(RegisterRequestDto(username.trim(), email?.trim()?.ifBlank { null }, password, displayName.trim())) }) {
            is ApiResult.Success -> {
                tokenStore.save(r.data.accessToken, r.data.refreshToken)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> r
        }

    suspend fun logout() {
        val refresh = tokenStore.refreshToken()
        if (!refresh.isNullOrBlank()) {
            runCatching { api.logout(LogoutRequestDto(refresh)) }
        }
        tokenStore.clear()
    }
}
