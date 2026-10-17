package com.zoomers.app.core.data

import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.CreateUploadSessionRequestDto
import com.zoomers.app.core.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the direct-to-storage upload handshake for a single image:
 *   open session -> PUT bytes to the presigned URL -> complete (server verifies).
 * Returns the completed upload-session id, which callers attach to a post or
 * profile update. The PUT uses a bare client (no auth header — the presigned URL
 * carries its own signature).
 */
@Singleton
class MediaUploader @Inject constructor(
    private val api: ZoomersApi,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** @param purpose backend UploadPurpose name, e.g. "PostMedia" or "Avatar". */
    suspend fun upload(image: NewPostImage, purpose: String): ApiResult<String> {
        val session = when (val r = safeApiCall {
            api.createUploadSession(CreateUploadSessionRequestDto(purpose, image.contentType))
        }) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }

        val uploaded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(session.uploadUrl)
                    .put(image.bytes.toRequestBody(image.contentType.toMediaType()))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
        if (!uploaded) {
            return ApiResult.Error("Couldn't upload the image. Please check your connection and try again.")
        }

        return when (val r = safeApiCall { api.completeUploadSession(session.sessionId) }) {
            is ApiResult.Success -> ApiResult.Success(session.sessionId)
            is ApiResult.Error -> r
        }
    }
}
