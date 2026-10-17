package com.zoomers.app.core.model

import com.zoomers.app.core.network.dto.CommentViewDto
import com.zoomers.app.core.network.dto.NotificationViewDto
import com.zoomers.app.core.network.dto.PostViewDto
import com.zoomers.app.core.network.dto.ProfileViewDto
import com.zoomers.app.core.network.dto.PublicProfileViewDto
import com.zoomers.app.core.network.dto.UserListItemDto

data class PostMediaUi(
    val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val isVideo: Boolean,
    val aspectRatio: Float, // width / height; defaults to 4:5 like the concept
)

data class PostUi(
    val id: String,
    val authorId: String,
    val username: String,
    val displayName: String,
    val isVerified: Boolean,
    val locationName: String?,
    val caption: String?,
    val createdAtIso: String,
    val likeCount: Int,
    val commentCount: Int,
    val hasLiked: Boolean,
    val hasSaved: Boolean,
    val media: List<PostMediaUi>,
) {
    val isCarousel: Boolean get() = media.size > 1
}

data class ProfileUi(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String?,
    val website: String?,
    val avatarUrl: String?,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val followerCount: Int,
    val followingCount: Int,
    val postCount: Int,
)

data class FeedPage(val items: List<PostUi>, val nextCursor: String?)

data class CommentUi(
    val id: String,
    val username: String,
    val displayName: String,
    val isVerified: Boolean,
    val body: String,
    val createdAtIso: String,
    val likeCount: Int,
    val replyCount: Int,
)

data class CommentPage(val items: List<CommentUi>, val nextCursor: String?)

data class UserListItemUi(
    val id: String,
    val username: String,
    val displayName: String,
    val isVerified: Boolean,
    val isPrivate: Boolean,
)

data class UserListPage(val items: List<UserListItemUi>, val nextCursor: String?)

enum class ActivityType { Like, Comment, Follow, FollowRequest, Unknown }

data class NotificationUi(
    val id: String,
    val type: ActivityType,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val postId: String?,
    val postThumbnailUrl: String?,
    val createdAtIso: String,
    val read: Boolean,
)

data class NotificationPage(val items: List<NotificationUi>, val nextCursor: String?)

/** Another user's profile, with the viewer's relationship for the follow button. */
data class PublicProfileUi(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String?,
    val website: String?,
    val avatarUrl: String?,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val followerCount: Int,
    val followingCount: Int,
    val postCount: Int,
    val isSelf: Boolean,
    val isFollowing: Boolean,
    val hasOutgoingRequest: Boolean,
    val canViewPosts: Boolean,
)

// ---- mappers ----

fun PostViewDto.toUi(): PostUi = PostUi(
    id = id,
    authorId = author.id,
    username = author.username,
    displayName = author.displayName,
    isVerified = author.isVerified,
    locationName = locationName,
    caption = caption,
    createdAtIso = createdAt,
    likeCount = likeCount,
    commentCount = commentCount,
    hasLiked = viewer.hasLiked,
    hasSaved = viewer.hasSaved,
    media = media.sortedBy { it.orderIndex }.map { m ->
        val ar = if (m.width != null && m.height != null && m.height > 0) {
            m.width.toFloat() / m.height.toFloat()
        } else 0.8f // 4:5 default
        PostMediaUi(m.id, m.url, m.thumbnailUrl, isVideo = m.type == 1, aspectRatio = ar)
    },
)

fun ProfileViewDto.toUi(): ProfileUi = ProfileUi(
    id, username, displayName, bio, website, avatarUrl, isPrivate, isVerified, followerCount, followingCount, postCount,
)

fun CommentViewDto.toUi(): CommentUi = CommentUi(
    id = id,
    username = author.username,
    displayName = author.displayName,
    isVerified = author.isVerified,
    body = body,
    createdAtIso = createdAt,
    likeCount = likeCount,
    replyCount = replyCount,
)

fun UserListItemDto.toUi(): UserListItemUi = UserListItemUi(id, username, displayName, isVerified, isPrivate)

fun NotificationViewDto.toUi(): NotificationUi = NotificationUi(
    id = id,
    type = when (type) {
        0 -> ActivityType.Like
        1 -> ActivityType.Comment
        2 -> ActivityType.Follow
        3 -> ActivityType.FollowRequest
        else -> ActivityType.Unknown
    },
    username = actor.username,
    displayName = actor.displayName,
    avatarUrl = actor.avatarUrl,
    postId = postId,
    postThumbnailUrl = postThumbnailUrl,
    createdAtIso = createdAt,
    read = read,
)

fun PublicProfileViewDto.toUi(): PublicProfileUi = PublicProfileUi(
    id = id,
    username = username,
    displayName = displayName,
    bio = bio,
    website = website,
    avatarUrl = avatarUrl,
    isPrivate = isPrivate,
    isVerified = isVerified,
    followerCount = followerCount,
    followingCount = followingCount,
    postCount = postCount,
    isSelf = relationship.isSelf,
    isFollowing = relationship.isFollowing,
    hasOutgoingRequest = relationship.hasOutgoingRequest,
    canViewPosts = canViewPosts,
)
