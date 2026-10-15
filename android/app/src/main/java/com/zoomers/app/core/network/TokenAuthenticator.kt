package com.zoomers.app.core.network

import com.zoomers.app.core.data.TokenStore
import com.zoomers.app.core.network.dto.RefreshRequestDto
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * On a 401, attempts a single refresh-token rotation and retries the request with
 * the new access token. If refresh fails, clears the session so the app routes to
 * sign-in. [Lazy] breaks the OkHttp ↔ Retrofit construction cycle.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val api: Lazy<ZoomersApi>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up after one retry to avoid loops.
        if (responseCount(response) >= 2) return null

        return runBlocking {
            val refresh = tokenStore.refreshToken() ?: return@runBlocking null
            val result = runCatching { api.get().refresh(RefreshRequestDto(refresh)) }.getOrNull()
            val body = result?.body()
            if (result != null && result.isSuccessful && body != null) {
                tokenStore.save(body.accessToken, body.refreshToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${body.accessToken}")
                    .build()
            } else {
                tokenStore.clear()
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
