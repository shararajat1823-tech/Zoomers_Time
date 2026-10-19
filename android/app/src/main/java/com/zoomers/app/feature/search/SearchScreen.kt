package com.zoomers.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.core.model.UserListItemUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.component.ZPillTabs
import com.zoomers.app.designsystem.theme.Shapes
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

@Composable
fun SearchScreen(
    onOpenUser: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenPost: (String) -> Unit = {},
    gridColumns: Int = 3,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg),
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textMuted) },
            singleLine = true,
            shape = Shapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.hairline,
                cursorColor = colors.primary,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
        )

        if (state.isSearching) {
            ZPillTabs(
                options = listOf("People", "Tags"),
                selectedIndex = if (state.mode == SearchMode.People) 0 else 1,
                onSelect = { viewModel.setMode(if (it == 0) SearchMode.People else SearchMode.Tags) },
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.isSearching -> ExploreGrid(
                    posts = state.explorePosts,
                    loading = state.exploreLoading,
                    gridColumns = gridColumns,
                    onOpenPost = onOpenPost,
                    onLoadMore = viewModel::loadMoreExplore,
                )
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = colors.textMuted)
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

@Composable
private fun ExploreGrid(
    posts: List<PostUi>,
    loading: Boolean,
    gridColumns: Int,
    onOpenPost: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = ZoomersTheme.colors
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= posts.size - (gridColumns * 2) && posts.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    when {
        loading && posts.isEmpty() -> com.zoomers.app.designsystem.component.PhotoGridSkeleton(columns = gridColumns)
        posts.isEmpty() -> CenteredHint("Nothing to explore yet — be the first to post!")
        else -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(posts, key = { it.id }) { post ->
                val imageUrl = post.media.firstOrNull()?.let { it.thumbnailUrl ?: it.url }
                Box(
                    Modifier.padding(1.dp).aspectRatio(1f).background(tintFor(post.id)).clickable { onOpenPost(post.id) },
                ) {
                    if (imageUrl != null) {
                        AsyncImage(model = imageUrl, contentDescription = post.caption, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    if (post.isCarousel) {
                        Icon(
                            Icons.Outlined.Collections,
                            contentDescription = "Multiple photos",
                            tint = colors.background,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                        )
                    }
                }
            }
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
