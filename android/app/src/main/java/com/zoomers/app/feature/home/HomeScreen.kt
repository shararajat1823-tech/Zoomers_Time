package com.zoomers.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.designsystem.component.EmptyState
import com.zoomers.app.designsystem.component.ErrorState
import com.zoomers.app.designsystem.component.SkeletonBox
import com.zoomers.app.designsystem.component.Wordmark
import com.zoomers.app.designsystem.component.ZPillTabs
import com.zoomers.app.designsystem.foundation.ZoomersLayout
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun HomeScreen(
    layout: ZoomersLayout,
    onOpenProfile: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors
    val listState = rememberLazyListState()

    // Infinite scroll: load more when within 3 items of the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= (state.posts.size - 3) && state.posts.isNotEmpty()
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    val contentWidth: Dp = layout.feedItemMaxWidth

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        HomeTopBar(onOpenNotifications = onOpenNotifications, onOpenMessages = onOpenMessages)

        // Center + width-cap the whole feed column on larger screens.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val columnMod = if (contentWidth != Dp.Unspecified) {
                Modifier.widthIn(max = contentWidth).fillMaxWidth()
            } else Modifier.fillMaxWidth()

            when {
                state.loading -> FeedSkeleton(modifier = columnMod)
                state.error != null && state.posts.isEmpty() ->
                    ErrorState(message = state.error!!, onRetry = viewModel::refresh, modifier = columnMod.padding(top = Spacing.xxl))
                state.posts.isEmpty() ->
                    EmptyState(
                        title = "Your feed is quiet",
                        subtitle = "Follow a few creators and their posts will show up here.",
                        modifier = columnMod.padding(top = Spacing.xxl),
                    )
                else -> LazyColumn(
                    state = listState,
                    modifier = columnMod,
                    contentPadding = PaddingValues(bottom = Spacing.xxl),
                ) {
                    item {
                        ZPillTabs(
                            options = listOf("Following", "For you"),
                            selectedIndex = if (state.selectedTab == FeedTab.Following) 0 else 1,
                            onSelect = { viewModel.selectTab(if (it == 0) FeedTab.Following else FeedTab.ForYou) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                    item { StoryRow(stories = state.stories, modifier = Modifier.fillMaxWidth()) }
                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLikeToggle = viewModel::toggleLike,
                            onSaveToggle = viewModel::toggleSave,
                            onOpenProfile = onOpenProfile,
                            onOpenComments = onOpenComments,
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onOpenNotifications: () -> Unit, onOpenMessages: () -> Unit) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(40.dp).clickableIcon(onOpenNotifications), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Notifications", tint = colors.text)
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.size(40.dp).clickableIcon(onOpenMessages), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Messages", tint = colors.text)
        }
    }
}

private fun Modifier.clickableIcon(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun FeedSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        repeat(3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(38.dp), cornerRadius = 50)
                Spacer(Modifier.width(Spacing.md))
                SkeletonBox(Modifier.height(14.dp).width(140.dp))
            }
            Spacer(Modifier.height(Spacing.sm))
            SkeletonBox(Modifier.fillMaxWidth().height(320.dp), cornerRadius = 16)
            SkeletonBox(Modifier.height(14.dp).width(200.dp))
        }
    }
}
