package com.zoomers.app.feature.hashtag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.designsystem.component.EmptyState
import com.zoomers.app.designsystem.component.ErrorState
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.feature.common.BackTopBar
import com.zoomers.app.feature.common.PostThumbGrid

@Composable
fun HashtagScreen(
    tag: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    gridColumns: Int = 3,
    viewModel: HashtagViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    LaunchedEffect(tag) { viewModel.load(tag) }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        BackTopBar(title = "#$tag", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = colors.textMuted)
                }
                state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.load(tag) }, modifier = Modifier.padding(top = Spacing.xxl))
                state.posts.isEmpty() -> EmptyState("No posts yet", "Be the first to post with #$tag.", modifier = Modifier.padding(top = Spacing.xxl))
                else -> PostThumbGrid(posts = state.posts, gridColumns = gridColumns, onOpenPost = onOpenPost)
            }
        }
    }
}
