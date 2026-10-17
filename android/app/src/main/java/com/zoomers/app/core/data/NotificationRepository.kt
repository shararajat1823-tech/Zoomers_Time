package com.zoomers.app.core.data

import com.zoomers.app.core.model.NotificationPage
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    suspend fun notifications(cursor: String? = null, limit: Int = 30): ApiResult<NotificationPage> =
        when (val r = safeApiCall { api.notifications(cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                NotificationPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    suspend fun markAllRead(): ApiResult<Unit> = safeApiCall { api.markNotificationsRead() }
}
