package com.zoomers.app.core.data

import com.zoomers.app.core.model.FeedPage
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    suspend fun followingFeed(cursor: String?, limit: Int = 10): ApiResult<FeedPage> =
        when (val r = safeApiCall { api.followingFeed(cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                FeedPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    suspend fun post(postId: String): ApiResult<PostUi> =
        when (val r = safeApiCall { api.getPost(postId) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun like(postId: String): ApiResult<Int> =
        when (val r = safeApiCall { api.likePost(postId) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.likeCount)
            is ApiResult.Error -> r
        }

    suspend fun unlike(postId: String): ApiResult<Int> =
        when (val r = safeApiCall { api.unlikePost(postId) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.likeCount)
            is ApiResult.Error -> r
        }

    suspend fun save(postId: String): ApiResult<Unit> = safeApiCall { api.savePost(postId) }
    suspend fun unsave(postId: String): ApiResult<Unit> = safeApiCall { api.unsavePost(postId) }
}
