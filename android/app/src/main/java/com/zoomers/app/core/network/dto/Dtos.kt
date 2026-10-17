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
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
)

@Serializable
data class UpdateProfileRequestDto(
    val displayName: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val isPrivate: Boolean? = null,
    val avatarUploadSessionId: String? = null,
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

// ---- Media upload + post creation ----

@Serializable
data class CreateUploadSessionRequestDto(val purpose: String, val contentType: String)

@Serializable
data class UploadSessionResponseDto(
    val sessionId: String,
    val storageKey: String,
    val uploadUrl: String,
    val expiresAt: String,
    val status: Int,
    val maxBytes: Long,
)

@Serializable
data class CreatePostMediaItemDto(
    val uploadSessionId: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
)

@Serializable
data class CreatePostRequestDto(
    val caption: String?,
    val visibility: Int,          // 0 public, 1 followers, 2 private
    val locationName: String? = null,
    val accessibilityText: String? = null,
    val media: List<CreatePostMediaItemDto>,
)

// ---- Comments ----

@Serializable
data class CommentViewDto(
    val id: String,
    val author: PostAuthorDto,
    val body: String,
    val parentCommentId: String? = null,
    val createdAt: String,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val viewerHasLiked: Boolean = false,
)

@Serializable
data class AddCommentRequestDto(val body: String, val parentCommentId: String? = null)

// ---- Users / social ----

@Serializable
data class UserListItemDto(
    val id: String,
    val username: String,
    val displayName: String,
    val isVerified: Boolean = false,
    val isPrivate: Boolean = false,
)

@Serializable
data class RelationshipStateDto(
    val isSelf: Boolean = false,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val hasOutgoingRequest: Boolean = false,
    val hasIncomingRequest: Boolean = false,
    val isBlocking: Boolean = false,
    val isBlockedBy: Boolean = false,
)

@Serializable
data class PublicProfileViewDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val website: String? = null,
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val isPrivate: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
    val relationship: RelationshipStateDto = RelationshipStateDto(),
    val canViewPosts: Boolean = false,
)

@Serializable
data class FollowResultDto(val outcome: Int) // 0 Following, 1 Requested, 2 AlreadyFollowing, 3 AlreadyRequested

// ---- Notifications / activity ----

@Serializable
data class NotificationActorDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
)

@Serializable
data class NotificationViewDto(
    val id: String,
    val type: Int, // 0 Like, 1 Comment, 2 Follow, 3 FollowRequest
    val actor: NotificationActorDto,
    val postId: String? = null,
    val postThumbnailUrl: String? = null,
    val createdAt: String,
    val read: Boolean = false,
)

// ---- Stories ----

@Serializable
data class CreateStoryRequestDto(val uploadSessionId: String)

@Serializable
data class StoryAuthorDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
)

@Serializable
data class StoryItemDto(
    val id: String,
    val mediaUrl: String,
    val createdAt: String,
    val viewed: Boolean = false,
)

@Serializable
data class StoryTrayDto(
    val author: StoryAuthorDto,
    val stories: List<StoryItemDto> = emptyList(),
    val hasUnseen: Boolean = false,
    val isSelf: Boolean = false,
)

// ---- Hashtags ----

@Serializable
data class HashtagResultDto(val tag: String, val usageCount: Int = 0)

// ---- Settings ----

@Serializable
data class SettingsViewDto(
    val theme: Int = 0,
    val language: String = "en",
    val whoCanComment: Int = 0,
    val whoCanMention: Int = 0,
    val whoCanMessage: Int = 0,
    val showOnlineStatus: Boolean = true,
    val pushLikes: Boolean = true,
    val pushComments: Boolean = true,
    val pushFollows: Boolean = true,
    val pushMessages: Boolean = true,
)

@Serializable
data class UpdateSettingsRequestDto(
    val showOnlineStatus: Boolean? = null,
    val pushLikes: Boolean? = null,
    val pushComments: Boolean? = null,
    val pushFollows: Boolean? = null,
    val pushMessages: Boolean? = null,
)

/** RFC 7807 problem response surfaced by the API on errors. */
@Serializable
data class ProblemDto(
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
    val code: String? = null,
)
