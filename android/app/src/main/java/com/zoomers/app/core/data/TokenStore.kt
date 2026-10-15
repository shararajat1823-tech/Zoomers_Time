package com.zoomers.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "zoomers_session")

/**
 * Persists the auth session. NOTE: this starter uses DataStore for simplicity;
 * before release, back the token storage with EncryptedSharedPreferences / the
 * Android Keystore (see SECURITY.md — "Store mobile credentials securely").
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[accessKey] }
    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { !it[refreshKey].isNullOrBlank() }

    suspend fun accessToken(): String? = context.dataStore.data.map { it[accessKey] }.first()
    suspend fun refreshToken(): String? = context.dataStore.data.map { it[refreshKey] }.first()

    suspend fun save(access: String, refresh: String) {
        context.dataStore.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
