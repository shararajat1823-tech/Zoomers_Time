package com.zoomers.app.feature.profile

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zoomers.app.core.common.formatCount
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.ProfileUi
import com.zoomers.app.designsystem.component.EmptyState
import com.zoomers.app.designsystem.component.ErrorState
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.component.ZSecondaryButton
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

@Composable
fun ProfileScreen(
    gridColumns: Int = 3,
    onOpenPost: (String) -> Unit = {},
    onEditProfile: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = ZoomersTheme.colors.textMuted)
            }
            state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(top = Spacing.xxl))
            state.profile != null -> ProfileContent(
                profile = state.profile!!,
                posts = state.posts,
                gridColumns = gridColumns,
                onLogout = viewModel::logout,
                onOpenPost = onOpenPost,
                onEditProfile = onEditProfile,
            )
            else -> EmptyState("No profile")
        }
    }
}

@Composable
private fun ProfileContent(
    profile: ProfileUi,
    posts: List<PostUi>,
    gridColumns: Int,
    onLogout: () -> Unit,
    onOpenPost: (String) -> Unit,
    onEditProfile: () -> Unit,
) {
    val colors = ZoomersTheme.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZAvatar(key = profile.username, size = 84.dp, imageUrl = profile.avatarUrl, hasStoryRing = true)
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ZSecondaryButton("Edit profile", onClick = onEditProfile, modifier = Modifier.weight(1f))
                    ZSecondaryButton("Log out", onClick = onLogout, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
        // Real post grid: first media of each post, loaded from the API.
        items(posts, key = { it.id }) { post ->
            val imageUrl = post.media.firstOrNull()?.let { it.thumbnailUrl ?: it.url }
            Box(
                Modifier
                    .padding(1.dp)
                    .aspectRatio(1f)
                    .background(tintFor(post.id))
                    .clickable { onOpenPost(post.id) },
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = post.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (post.isCarousel) {
                    Icon(
                        imageVector = Icons.Outlined.Collections,
                        contentDescription = "Multiple photos",
                        tint = ZoomersTheme.colors.background,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                    )
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
