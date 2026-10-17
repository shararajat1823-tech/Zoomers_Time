package com.zoomers.app.core.data

import com.zoomers.app.core.network.ApiResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the signed-in user's id so screens can tell "is this mine?" (for delete
 * actions) without repeatedly hitting /users/me.
 */
@Singleton
class CurrentUserProvider @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    @Volatile
    private var cachedId: String? = null

    suspend fun userId(): String? {
        cachedId?.let { return it }
        return when (val r = profileRepository.me()) {
            is ApiResult.Success -> r.data.id.also { cachedId = it }
            is ApiResult.Error -> null
        }
    }

    fun clear() { cachedId = null }
}
