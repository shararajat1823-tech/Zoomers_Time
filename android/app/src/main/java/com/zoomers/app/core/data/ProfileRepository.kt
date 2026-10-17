package com.zoomers.app.core.data

import com.zoomers.app.core.model.FeedPage
import com.zoomers.app.core.model.ProfileUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    suspend fun me(): ApiResult<ProfileUi> =
        when (val r = safeApiCall { api.me() }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun userPosts(userId: String, cursor: String? = null, limit: Int = 24): ApiResult<FeedPage> =
        when (val r = safeApiCall { api.userPosts(userId, cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                FeedPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }
}
