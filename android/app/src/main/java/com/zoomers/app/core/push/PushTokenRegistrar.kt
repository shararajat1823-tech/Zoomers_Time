package com.zoomers.app.core.push

import com.google.firebase.messaging.FirebaseMessaging
import com.zoomers.app.core.data.DeviceRepository
import com.zoomers.app.core.data.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the backend's record of this device's FCM token in sync. Everything is
 * best-effort and guarded: if Firebase isn't configured (placeholder
 * google-services.json) or the user isn't signed in, these calls quietly no-op,
 * so the app behaves normally until push is set up.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val devices: DeviceRepository,
    private val tokenStore: TokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fetches the current FCM token and registers it (call on launch and after login). */
    fun syncToken() {
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> scope.launch { registerIfSignedIn(token) } }
        }
    }

    /** Called by the messaging service when FCM rotates the token. */
    fun onNewToken(token: String) {
        scope.launch { registerIfSignedIn(token) }
    }

    /** Removes the current token from the backend (call on sign-out). */
    fun unregister() {
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> scope.launch { runCatching { devices.remove(token) } } }
        }
    }

    private suspend fun registerIfSignedIn(token: String?) {
        if (token.isNullOrBlank()) return
        if (tokenStore.accessToken().isNullOrBlank()) return
        runCatching { devices.register(token) }
    }
}
