package com.zoomers.app.core.model

import com.zoomers.app.core.network.dto.PostViewDto
import com.zoomers.app.core.network.dto.ProfileViewDto

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
