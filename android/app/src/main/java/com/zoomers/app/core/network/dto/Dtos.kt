package com.zoomers.app.core.network.dto

import kotlinx.serialization.Serializable

// ---- Auth ----

@Serializable
data class RegisterRequestDto(
    val username: String,
    val email: String?,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequestDto(val usernameOrEmail: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class LogoutRequestDto(val refreshToken: String)

@Serializable
data class UserSummaryDto(
    val id: String,
    val username: String,
    val displayName: String,
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: UserSummaryDto,
)

// ---- Paging ----

@Serializable
data class CursorPageDto<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
)

// ---- Profile ----

@Serializable
data class ProfileViewDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val website: String? = null,
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
)

// ---- Posts ----

@Serializable
data class PostAuthorDto(
    val id: String,
    val username: String,
    val displayName: String,
    val isVerified: Boolean = false,
)

@Serializable
data class PostMediaViewDto(
    val id: String,
    val type: Int,            // 0 = image, 1 = video
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
    val orderIndex: Int = 0,
)

@Serializable
data class ViewerPostStateDto(val hasLiked: Boolean = false, val hasSaved: Boolean = false)

@Serializable
data class PostViewDto(
    val id: String,
    val author: PostAuthorDto,
    val type: Int,            // 0 image, 1 video, 2 carousel
    val visibility: Int,      // 0 public, 1 followers, 2 private
    val caption: String? = null,
    val locationName: String? = null,
    val accessibilityText: String? = null,
    val createdAt: String,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val media: List<PostMediaViewDto> = emptyList(),
    val viewer: ViewerPostStateDto = ViewerPostStateDto(),
)

@Serializable
data class LikeStateDto(val liked: Boolean, val likeCount: Int)

/** RFC 7807 problem response surfaced by the API on errors. */
@Serializable
data class ProblemDto(
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
    val code: String? = null,
)
