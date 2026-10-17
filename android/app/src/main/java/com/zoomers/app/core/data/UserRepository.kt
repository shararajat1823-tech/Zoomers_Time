package com.zoomers.app.core.data

import com.zoomers.app.core.model.FeedPage
import com.zoomers.app.core.model.PublicProfileUi
import com.zoomers.app.core.model.UserListPage
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    suspend fun searchUsers(query: String, cursor: String? = null, limit: Int = 20): ApiResult<UserListPage> =
        when (val r = safeApiCall { api.searchUsers(query, cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                UserListPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    suspend fun suggested(cursor: String? = null, limit: Int = 20): ApiResult<UserListPage> =
        when (val r = safeApiCall { api.suggestedUsers(cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                UserListPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    suspend fun profile(username: String): ApiResult<PublicProfileUi> =
        when (val r = safeApiCall { api.userByUsername(username) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun posts(userId: String, cursor: String? = null, limit: Int = 24): ApiResult<FeedPage> =
        when (val r = safeApiCall { api.userPosts(userId, cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                FeedPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    /** Returns the follow outcome code (0 Following, 1 Requested, …). */
    suspend fun follow(userId: String): ApiResult<Int> =
        when (val r = safeApiCall { api.follow(userId) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.outcome)
            is ApiResult.Error -> r
        }

    suspend fun unfollow(userId: String): ApiResult<Unit> = safeApiCall { api.unfollow(userId) }
}
