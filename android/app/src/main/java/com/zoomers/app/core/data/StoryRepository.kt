package com.zoomers.app.core.data

import com.zoomers.app.core.model.StoryTrayUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.CreateStoryRequestDto
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryRepository @Inject constructor(
    private val api: ZoomersApi,
    private val uploader: MediaUploader,
) {
    suspend fun feed(): ApiResult<List<StoryTrayUi>> =
        when (val r = safeApiCall { api.storyFeed() }) {
            is ApiResult.Success -> ApiResult.Success(r.data.map { it.toUi() })
            is ApiResult.Error -> r
        }

    /** Uploads the image and publishes a 24-hour story. */
    suspend fun create(image: NewPostImage): ApiResult<Unit> {
        val sessionId = when (val r = uploader.upload(image, purpose = "StoryMedia")) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
        }
        return safeApiCall { api.createStory(CreateStoryRequestDto(sessionId)) }
    }

    suspend fun markViewed(storyId: String): ApiResult<Unit> = safeApiCall { api.markStoryViewed(storyId) }
}
