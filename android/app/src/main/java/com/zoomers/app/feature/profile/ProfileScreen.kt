package com.zoomers.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zoomers.app.core.common.formatCount
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.ProfileUi
import com.zoomers.app.designsystem.component.EmptyState
import com.zoomers.app.designsystem.component.ErrorState
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.AccentGradient
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

@Composable
fun ProfileScreen(
    gridColumns: Int = 3,
    onOpenPost: (String) -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            state.loading -> com.zoomers.app.designsystem.component.PhotoGridSkeleton(columns = gridColumns)
            state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(top = Spacing.xxl))
            state.profile != null -> ProfileContent(
                state = state,
                gridColumns = gridColumns,
                onLogout = viewModel::logout,
                onOpenPost = onOpenPost,
                onEditProfile = onEditProfile,
                onOpenSettings = onOpenSettings,
                onSelectTab = viewModel::selectTab,
            )
            else -> EmptyState("No profile")
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    gridColumns: Int,
    onLogout: () -> Unit,
    onOpenPost: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTab: (ProfileTab) -> Unit,
) {
    val profile = state.profile!!
    val shown = if (state.tab == ProfileTab.Grid) state.posts else state.saved

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
    ) {
        // ---- header (banner + avatar + stats + bio + actions) ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(profile = profile, onEditProfile = onEditProfile, onLogout = onLogout, onOpenSettings = onOpenSettings)
        }
        // ---- tab bar (Grid / Saved) ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileTabBar(selected = state.tab, onSelect = onSelectTab)
        }

        // ---- empty / loading states for the active tab ----
        if (state.tab == ProfileTab.Saved && state.savedLoading && shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp), color = ZoomersTheme.colors.accent)
                }
            }
        } else if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val msg = if (state.tab == ProfileTab.Grid) "No posts yet" else "No saved posts yet"
                val icon = if (state.tab == ProfileTab.Grid) Icons.Filled.GridView else Icons.Outlined.BookmarkBorder
                EmptyState(msg, icon = icon)
            }
        }

        // ---- the grid of photos ----
        items(shown, key = { it.id }) { post ->
            PostThumb(post = post, onOpenPost = onOpenPost)
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: ProfileUi,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = ZoomersTheme.colors
    Box(Modifier.fillMaxWidth()) {
        // Decorative gradient banner.
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3A2170), Color(0xFF241A42), colors.background),
                    ),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x334D6CFE), Color(0x00141A2A), Color(0x339B4DFF)),
                        ),
                    ),
            )
        }
        // Settings gear over the banner.
        Icon(
            Icons.Outlined.Settings,
            contentDescription = "Settings",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(Spacing.lg)
                .size(26.dp)
                .clickable(onClick = onOpenSettings),
        )

        // Foreground content, pulled up so the avatar overlaps the banner.
        Column(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = 84.dp, bottom = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    ZAvatar(key = profile.username, size = 92.dp, imageUrl = profile.avatarUrl, hasStoryRing = true)
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(AccentGradient))
                            .border(2.5.dp, colors.background, CircleShape)
                            .clickable(onClick = onEditProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(Spacing.lg))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Stat("Posts", profile.postCount)
                    StatDivider()
                    Stat("Followers", profile.followerCount)
                    StatDivider()
                    Stat("Following", profile.followingCount)
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                )
                if (profile.isVerified) {
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = colors.accent, modifier = Modifier.size(20.dp))
                }
            }
            if (!profile.bio.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            }
            if (!profile.website.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(profile.website, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GradientPillButton(
                    text = "Edit profile",
                    icon = Icons.Filled.Edit,
                    onClick = onEditProfile,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, colors.hairline),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Log out", color = colors.text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GradientPillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(AccentGradient))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.height(30.dp).width(1.dp).background(ZoomersTheme.colors.hairline))
}

@Composable
private fun Stat(label: String, value: Int) {
    val colors = ZoomersTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(value), style = MaterialTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

@Composable
private fun ProfileTabBar(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        TabCell(
            modifier = Modifier.weight(1f),
            selected = selected == ProfileTab.Grid,
            icon = if (selected == ProfileTab.Grid) Icons.Filled.GridView else Icons.Outlined.GridView,
            onClick = { onSelect(ProfileTab.Grid) },
        )
        TabCell(
            modifier = Modifier.weight(1f),
            selected = selected == ProfileTab.Saved,
            icon = if (selected == ProfileTab.Saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            onClick = { onSelect(ProfileTab.Saved) },
        )
    }
}

@Composable
private fun TabCell(modifier: Modifier, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.text else colors.textMuted,
            modifier = Modifier.padding(vertical = Spacing.md).size(24.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) Brush.linearGradient(AccentGradient) else androidx.compose.ui.graphics.SolidColor(colors.hairline)),
        )
    }
}

@Composable
private fun PostThumb(post: PostUi, onOpenPost: (String) -> Unit) {
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
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
            )
        }
    }
}
