package com.zoomers.app.core.data

import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.CreatePostMediaItemDto
import com.zoomers.app.core.network.dto.CreatePostRequestDto
import com.zoomers.app.core.network.safeApiCall
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
 * Creates posts. Each image is uploaded directly to object storage via
 * [MediaUploader]; the resulting session ids are then referenced by one post
 * (1–10 media items). The feed/profile already render carousels.
 */
@Singleton
class PostRepository @Inject constructor(
    private val api: ZoomersApi,
    private val uploader: MediaUploader,
    private val feedRefreshBus: FeedRefreshBus,
) {
    suspend fun createImagesPost(
        images: List<NewPostImage>,
        caption: String?,
        visibility: Int,
    ): ApiResult<PostUi> {
        if (images.isEmpty()) return ApiResult.Error("Pick at least one photo to share.")

        val mediaItems = ArrayList<CreatePostMediaItemDto>(images.size)
        for (image in images) {
            val sessionId = when (val r = uploader.upload(image, purpose = "PostMedia")) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> return r
            }
            mediaItems += CreatePostMediaItemDto(
                uploadSessionId = sessionId,
                width = image.width,
                height = image.height,
            )
        }

        val body = CreatePostRequestDto(
            caption = caption?.trim()?.ifBlank { null },
            visibility = visibility,
            media = mediaItems,
        )
        return when (val r = safeApiCall { api.createPost(body) }) {
            is ApiResult.Success -> {
                feedRefreshBus.requestRefresh()
                ApiResult.Success(r.data.toUi())
            }
            is ApiResult.Error -> r
        }
    }

    suspend fun delete(postId: String): ApiResult<Unit> {
        val r = safeApiCall { api.deletePost(postId) }
        if (r is ApiResult.Success) feedRefreshBus.requestRefresh()
        return r
    }
}
