package com.zoomers.app.core.data

import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.CreatePostMediaItemDto
import com.zoomers.app.core.network.dto.CreatePostRequestDto
import com.zoomers.app.core.network.dto.CreateUploadSessionRequestDto
import com.zoomers.app.core.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A photo ready to post: raw bytes (already compressed) plus its content type and pixel size. */
data class NewPostImage(
    val bytes: ByteArray,
    val contentType: String,
    val width: Int,
    val height: Int,
)

/**
 * Creates posts. The flow mirrors the backend's direct-to-storage design:
 *  1. open an upload session (gets a short-lived presigned upload URL),
 *  2. PUT the bytes straight to object storage (not through the API),
 *  3. mark the session complete (server verifies size/type),
 *  4. create the post referencing the session.
 */
@Singleton
class PostRepository @Inject constructor(
    private val api: ZoomersApi,
    private val feedRefreshBus: FeedRefreshBus,
) {
    // A bare client for the direct upload: no auth header (the presigned URL
    // carries its own signature) and generous timeouts for large images.
    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun createImagePost(
        image: NewPostImage,
        caption: String?,
        visibility: Int,
    ): ApiResult<PostUi> {
        // 1. Upload session.
        val session = when (val r = safeApiCall {
            api.createUploadSession(CreateUploadSessionRequestDto("PostMedia", image.contentType))
        }) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }

        // 2. PUT the bytes to the presigned URL.
        val uploaded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(session.uploadUrl)
                    .put(image.bytes.toRequestBody(image.contentType.toMediaType()))
                    .build()
                uploadClient.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
        if (!uploaded) {
            return ApiResult.Error("Couldn't upload the photo. Please check your connection and try again.")
        }

        // 3. Complete (server-side verification).
        when (val r = safeApiCall { api.completeUploadSession(session.sessionId) }) {
            is ApiResult.Success -> Unit
            is ApiResult.Error -> return r
        }

        // 4. Create the post.
        val body = CreatePostRequestDto(
            caption = caption?.trim()?.ifBlank { null },
            visibility = visibility,
            media = listOf(
                CreatePostMediaItemDto(
                    uploadSessionId = session.sessionId,
                    width = image.width,
                    height = image.height,
                ),
            ),
        )
        return when (val r = safeApiCall { api.createPost(body) }) {
            is ApiResult.Success -> {
                feedRefreshBus.requestRefresh()
                ApiResult.Success(r.data.toUi())
            }
            is ApiResult.Error -> r
        }
    }
}
