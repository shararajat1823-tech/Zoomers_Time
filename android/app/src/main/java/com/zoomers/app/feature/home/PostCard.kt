package com.zoomers.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zoomers.app.core.common.captionAnnotated
import com.zoomers.app.core.common.formatCount
import com.zoomers.app.core.common.relativeTime
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun PostCard(
    post: PostUi,
    onLikeToggle: (PostUi) -> Unit,
    onSaveToggle: (PostUi) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZoomersTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZAvatar(key = post.username, size = 38.dp, modifier = Modifier.clickable { onOpenProfile(post.username) })
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(post.username, style = MaterialTheme.typography.titleMedium, color = colors.text)
                if (!post.locationName.isNullOrBlank()) {
                    Text(post.locationName, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = colors.text)
        }

        // Media (single or carousel)
        if (post.media.isNotEmpty()) {
            val ar = post.media.first().aspectRatio.coerceIn(0.6f, 1.25f)
            val pagerState = rememberPagerState(pageCount = { post.media.size })
            var showHeart by remember(post.id) { mutableStateOf(false) }
            LaunchedEffect(showHeart) {
                if (showHeart) { delay(650); showHeart = false }
            }
            Box(
                modifier = Modifier.pointerInput(post.id) {
                    detectTapGestures(onDoubleTap = {
                        if (!post.hasLiked) onLikeToggle(post)
                        showHeart = true
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    })
                },
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    val media = post.media[page]
                    AsyncImage(
                        model = media.thumbnailUrl ?: media.url,
                        contentDescription = post.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ar)
                            .background(ZoomersTheme.colors.hairline),
                    )
                }
                // Double-tap like burst.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHeart,
                    enter = androidx.compose.animation.scaleIn(initialScale = 0.5f) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(96.dp),
                    )
                }
                if (post.isCarousel) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.md)
                            .clip(RoundedCornerShape(50))
                            .background(colors.text.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${pagerState.currentPage + 1}/${post.media.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.background,
                        )
                    }
                }
            }
        }

        // Actions
        val likeScale = remember(post.id) { Animatable(1f) }
        var prevLiked by remember(post.id) { mutableStateOf(post.hasLiked) }
        LaunchedEffect(post.hasLiked) {
            if (post.hasLiked && !prevLiked) {
                likeScale.snapTo(0.7f)
                likeScale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
            }
            prevLiked = post.hasLiked
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(
                icon = if (post.hasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                tint = if (post.hasLiked) colors.like else colors.text,
                contentDescription = "Like",
                onClick = {
                    onLikeToggle(post)
                    if (!post.hasLiked) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                scale = likeScale.value,
            )
            ActionIcon(Icons.Outlined.ChatBubbleOutline, colors.text, "Comment") { onOpenComments(post.id) }
            ActionIcon(Icons.AutoMirrored.Outlined.Send, colors.text, "Share") {}
            Spacer(Modifier.weight(1f))
            ActionIcon(
                icon = if (post.hasSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                tint = colors.text,
                contentDescription = "Save",
                onClick = { onSaveToggle(post) },
            )
        }

        // Likes + caption + comments + time
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            if (post.likeCount > 0) {
                androidx.compose.animation.AnimatedContent(
                    targetState = post.likeCount,
                    transitionSpec = {
                        (androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn())
                            .togetherWith(androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut())
                    },
                    label = "likeCount",
                ) { count ->
                    Text(
                        "${formatCount(count)} likes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                }
                Spacer(Modifier.size(Spacing.xs))
            }
            if (!post.caption.isNullOrBlank()) {
                Text(
                    captionAnnotated(post.username, post.caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                )
            }
            if (post.commentCount > 0) {
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    "View all ${formatCount(post.commentCount)} comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.clickable { onOpenComments(post.id) },
                )
            }
            Spacer(Modifier.size(Spacing.xs))
            Text(relativeTime(post.createdAtIso), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp).scale(scale))
    }
}
