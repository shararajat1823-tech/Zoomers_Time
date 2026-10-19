package com.zoomers.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zoomers.app.core.model.GalleryItem
import com.zoomers.app.core.model.UserListItemUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.component.ZPillTabs
import com.zoomers.app.designsystem.theme.AccentGradient
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

@Composable
fun SearchScreen(
    onOpenUser: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenPost: (String) -> Unit = {},
    onOpenReels: () -> Unit = {},
    gridColumns: Int = 3,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Text(
            "Explore",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colors.text,
            modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs),
        )

        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )

        if (state.isSearching) {
            ZPillTabs(
                options = listOf("People", "Tags"),
                selectedIndex = if (state.mode == SearchMode.People) 0 else 1,
                onSelect = { viewModel.setMode(if (it == 0) SearchMode.People else SearchMode.Tags) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        } else {
            FilterChipsRow(selected = state.galleryTab, onSelect = viewModel::selectGalleryTab)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.isSearching -> GalleryGrid(
                    items = state.gallery,
                    loading = state.galleryLoading,
                    refreshing = state.galleryRefreshing,
                    onOpen = { item ->
                        if (item.isVideo) onOpenReels() else item.postId?.let(onOpenPost)
                    },
                    onLoadMore = viewModel::loadMoreGallery,
                    onRefresh = viewModel::refreshGallery,
                )
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                state.mode == SearchMode.People -> if (state.users.isEmpty()) {
                    CenteredHint("No people found")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.users, key = { it.id }) { user ->
                            UserRow(user, onClick = { onOpenUser(user.username) })
                        }
                    }
                }
                else -> if (state.hashtags.isEmpty()) {
                    CenteredHint("No hashtags found")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.hashtags, key = { it.tag }) { tag ->
                            HashtagRow(tag.tag, tag.usageCount, onClick = { onOpenHashtag(tag.tag) })
                        }
                    }
                }
            }
        }
    }
}

// ---- Filter chips (All / Photos / Videos / Favorites) ----

@Composable
private fun FilterChipsRow(selected: GalleryTab, onSelect: (GalleryTab) -> Unit) {
    val chips = listOf(
        GalleryTab.All to Icons.Outlined.GridView,
        GalleryTab.Photos to Icons.Outlined.PhotoLibrary,
        GalleryTab.Videos to Icons.Outlined.PlayCircle,
        GalleryTab.Favorites to Icons.Outlined.FavoriteBorder,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(chips, key = { it.first.name }) { (tab, icon) ->
            FilterChip(label = tab.name, icon = icon, selected = tab == selected, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun FilterChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    val base = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    val filled = if (selected) {
        base.background(Brush.linearGradient(AccentGradient))
    } else {
        base.background(colors.surfaceElevated)
    }
    Row(
        modifier = filled.padding(horizontal = Spacing.lg, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else colors.textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else colors.text,
        )
    }
}

// ---- Staggered gallery grid ----

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
private fun GalleryGrid(
    items: List<GalleryItem>,
    loading: Boolean,
    refreshing: Boolean,
    onOpen: (GalleryItem) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = ZoomersTheme.colors
    val gridState = rememberLazyStaggeredGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= items.size - 4 && items.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    when {
        loading && items.isEmpty() -> com.zoomers.app.designsystem.component.PhotoGridSkeleton(columns = 2)
        items.isEmpty() -> CenteredHint("Nothing here yet.")
        else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalItemSpacing = Spacing.sm,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items, key = { it.id }) { item ->
                    GalleryCard(item = item, onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun GalleryCard(item: GalleryItem, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(item.aspectRatio.coerceIn(0.66f, 1.4f))
            .clip(RoundedCornerShape(14.dp))
            .background(tintFor(item.id))
            .clickable(onClick = onClick),
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Top-right menu.
        OverlayCircle(Modifier.align(Alignment.TopEnd).padding(Spacing.sm)) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }

        // Center play glyph for videos.
        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        // Bottom-left badge: duration (video) or photo count (carousel).
        val badge: Pair<ImageVector, String>? = when {
            item.isVideo && item.durationLabel != null -> Icons.Filled.PlayArrow to item.durationLabel!!
            item.photoCount > 1 -> Icons.Outlined.Collections to item.photoCount.toString()
            else -> null
        }
        if (badge != null) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(Spacing.sm)
                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = Spacing.sm, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(badge.first, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(badge.second, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }

        // Bottom-right favorite.
        if (item.isFavorite) {
            OverlayCircle(Modifier.align(Alignment.BottomEnd).padding(Spacing.sm)) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun OverlayCircle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

// ---- Compact search field ----

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ZoomersTheme.colors
    val fill = if (colors.isDark) Color(0xFF1C2436) else Color(0xFFEFEFEF)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search people and tags", color = colors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = ZoomersTheme.colors.textMuted)
    }
}

@Composable
private fun HashtagRow(tag: String, usageCount: Int, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = MaterialTheme.typography.titleLarge, color = colors.primary, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(start = Spacing.md)) {
            Text(tag, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text("$usageCount posts", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun UserRow(user: UserListItemUi, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZAvatar(key = user.username, size = 44.dp)
        Column(Modifier.padding(start = Spacing.md)) {
            Text(user.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(user.displayName, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
    }
}
