package com.zoomers.app.core.data

import com.zoomers.app.core.model.CommentPage
import com.zoomers.app.core.model.CommentUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.AddCommentRequestDto
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    suspend fun comments(postId: String, cursor: String? = null, limit: Int = 30): ApiResult<CommentPage> =
        when (val r = safeApiCall { api.comments(postId, cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                CommentPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    suspend fun add(postId: String, body: String): ApiResult<CommentUi> =
        when (val r = safeApiCall { api.addComment(postId, AddCommentRequestDto(body.trim())) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun delete(commentId: String): ApiResult<Unit> = safeApiCall { api.deleteComment(commentId) }
}
