package com.zoomers.app.core.data

import com.zoomers.app.core.model.ReelPage
import com.zoomers.app.core.model.ReelUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.CreateReelRequestDto
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReelRepository @Inject constructor(
    private val api: ZoomersApi,
    private val uploader: MediaUploader,
) {
    suspend fun feed(cursor: String? = null, limit: Int = 10): ApiResult<ReelPage> =
        when (val r = safeApiCall { api.reelsFeed(cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                ReelPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    /**
     * Uploads the picked video (via the shared media pipeline) and publishes a reel.
     * [video] carries the raw bytes + probed dimensions; [durationMs] comes from the
     * client's metadata probe.
     */
    suspend fun create(video: NewPostImage, caption: String?, durationMs: Int): ApiResult<ReelUi> {
        val sessionId = when (val r = uploader.upload(video, "ReelVideo")) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }
        val body = CreateReelRequestDto(
            uploadSessionId = sessionId,
            thumbnailUploadSessionId = null,
            caption = caption?.trim()?.ifBlank { null },
            width = video.width,
            height = video.height,
            durationMs = durationMs,
        )
        return when (val r = safeApiCall { api.createReel(body) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }
    }

    suspend fun like(reelId: String): ApiResult<Int> =
        when (val r = safeApiCall { api.likeReel(reelId) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.likeCount)
            is ApiResult.Error -> r
        }

    suspend fun unlike(reelId: String): ApiResult<Int> =
        when (val r = safeApiCall { api.unlikeReel(reelId) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.likeCount)
            is ApiResult.Error -> r
        }

    suspend fun delete(reelId: String): ApiResult<Unit> = safeApiCall { api.deleteReel(reelId) }
}
