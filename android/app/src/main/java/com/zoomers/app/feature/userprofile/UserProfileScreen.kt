package com.zoomers.app.feature.userprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zoomers.app.core.common.formatCount
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.PublicProfileUi
import com.zoomers.app.designsystem.component.ErrorState
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.component.ZPillButton
import com.zoomers.app.designsystem.component.ZSecondaryButton
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

@Composable
fun UserProfileScreen(
    username: String,
    onBack: () -> Unit,
    gridColumns: Int = 3,
    onOpenPost: (String) -> Unit = {},
    onMessage: (userId: String) -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    LaunchedEffect(username) { viewModel.load(username) }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(Spacing.sm).clickable(onClick = onBack)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text(
                state.profile?.username ?: username,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = colors.textMuted)
                }
                state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.load(username) }, modifier = Modifier.padding(top = Spacing.xxl))
                state.profile != null -> Content(
                    profile = state.profile!!,
                    posts = state.posts,
                    gridColumns = gridColumns,
                    followBusy = state.followBusy,
                    onToggleFollow = viewModel::toggleFollow,
                    onOpenPost = onOpenPost,
                    onMessage = onMessage,
                )
            }
        }
    }
}

@Composable
private fun Content(
    profile: PublicProfileUi,
    posts: List<PostUi>,
    gridColumns: Int,
    followBusy: Boolean,
    onToggleFollow: () -> Unit,
    onOpenPost: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val colors = ZoomersTheme.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZAvatar(key = profile.username, size = 84.dp, imageUrl = profile.avatarUrl)
                    Spacer(Modifier.width(Spacing.xl))
                    Stat("Posts", profile.postCount)
                    Stat("Followers", profile.followerCount)
                    Stat("Following", profile.followingCount)
                }
                Spacer(Modifier.height(Spacing.md))
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
                if (!profile.bio.isNullOrBlank()) {
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                }
                if (!profile.website.isNullOrBlank()) {
                    Text(profile.website, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
                }
                Spacer(Modifier.height(Spacing.md))
                if (!profile.isSelf) {
                    val label = when {
                        profile.isFollowing -> "Following"
                        profile.hasOutgoingRequest -> "Requested"
                        else -> "Follow"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (profile.isFollowing || profile.hasOutgoingRequest) {
                            ZSecondaryButton(label, onClick = onToggleFollow, modifier = Modifier.weight(1f))
                        } else {
                            ZPillButton(label, onClick = onToggleFollow, modifier = Modifier.weight(1f))
                        }
                        ZSecondaryButton("Message", onClick = { onMessage(profile.id) }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }

        if (!profile.canViewPosts) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textMuted)
                    Text("This account is private", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Text("Follow to see their posts.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            }
        } else {
            items(posts, key = { it.id }) { post ->
                val imageUrl = post.media.firstOrNull()?.let { it.thumbnailUrl ?: it.url }
                Box(
                    Modifier.padding(1.dp).aspectRatio(1f).background(tintFor(post.id)).clickable { onOpenPost(post.id) },
                ) {
                    if (imageUrl != null) {
                        AsyncImage(model = imageUrl, contentDescription = post.caption, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    if (post.isCarousel) {
                        Icon(Icons.Outlined.Collections, contentDescription = "Multiple photos", tint = colors.background, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    val colors = ZoomersTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = Spacing.md)) {
        Text(formatCount(value), style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}
