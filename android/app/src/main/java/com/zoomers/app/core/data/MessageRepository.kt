package com.zoomers.app.core.data

import com.zoomers.app.core.model.ConversationPage
import com.zoomers.app.core.model.ConversationUi
import com.zoomers.app.core.model.MessagePage
import com.zoomers.app.core.model.MessageUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.SendMessageRequestDto
import com.zoomers.app.core.network.dto.StartConversationRequestDto
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Direct-messaging data: inbox, threads, send, and read receipts. */
@Singleton
class MessageRepository @Inject constructor(
    private val api: ZoomersApi,
) {
    /** Gets or creates the 1:1 conversation with [userId] (idempotent). */
    suspend fun start(userId: String): ApiResult<ConversationUi> =
        when (val r = safeApiCall { api.startConversation(StartConversationRequestDto(userId)) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun conversations(cursor: String? = null, limit: Int = 30): ApiResult<ConversationPage> =
        when (val r = safeApiCall { api.conversations(cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                ConversationPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    suspend fun messages(conversationId: String, cursor: String? = null, limit: Int = 30): ApiResult<MessagePage> =
        when (val r = safeApiCall { api.messages(conversationId, cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                MessagePage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    suspend fun send(conversationId: String, body: String): ApiResult<MessageUi> =
        when (val r = safeApiCall { api.sendMessage(conversationId, SendMessageRequestDto(body.trim())) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun markRead(conversationId: String): ApiResult<Unit> =
        safeApiCall { api.markConversationRead(conversationId) }

    suspend fun unreadCount(): ApiResult<Int> =
        when (val r = safeApiCall { api.conversationsUnreadCount() }) {
            is ApiResult.Success -> ApiResult.Success(r.data.count)
            is ApiResult.Error -> r
        }
}
