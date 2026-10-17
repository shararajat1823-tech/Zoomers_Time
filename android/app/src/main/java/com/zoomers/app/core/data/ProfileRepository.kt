package com.zoomers.app.core.data

import com.zoomers.app.core.model.FeedPage
import com.zoomers.app.core.model.ProfileUi
import com.zoomers.app.core.model.toUi
import com.zoomers.app.core.network.ApiResult
import com.zoomers.app.core.network.ZoomersApi
import com.zoomers.app.core.network.dto.UpdateProfileRequestDto
import com.zoomers.app.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: ZoomersApi,
    private val uploader: MediaUploader,
) {
    suspend fun me(): ApiResult<ProfileUi> =
        when (val r = safeApiCall { api.me() }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }

    suspend fun userPosts(userId: String, cursor: String? = null, limit: Int = 24): ApiResult<FeedPage> =
        when (val r = safeApiCall { api.userPosts(userId, cursor, limit) }) {
            is ApiResult.Success -> ApiResult.Success(
                FeedPage(items = r.data.items.map { it.toUi() }, nextCursor = r.data.nextCursor),
            )
            is ApiResult.Error -> r
        }

    /**
     * Saves profile fields and, if [newAvatar] is provided, uploads it first and
     * attaches the resulting session so the server sets it as the avatar.
     */
    suspend fun updateProfile(
        displayName: String,
        bio: String?,
        website: String?,
        newAvatar: NewPostImage? = null,
    ): ApiResult<ProfileUi> {
        var avatarSessionId: String? = null
        if (newAvatar != null) {
            when (val r = uploader.upload(newAvatar, purpose = "Avatar")) {
                is ApiResult.Success -> avatarSessionId = r.data
                is ApiResult.Error -> return r
            }
        }

        // Send empty strings (not null) so the backend clears a field the user
        // emptied; null would mean "leave unchanged".
        val body = UpdateProfileRequestDto(
            displayName = displayName.trim(),
            bio = (bio ?: "").trim(),
            website = (website ?: "").trim(),
            avatarUploadSessionId = avatarSessionId,
        )
        return when (val r = safeApiCall { api.updateMe(body) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.toUi())
            is ApiResult.Error -> r
        }
    }
}
