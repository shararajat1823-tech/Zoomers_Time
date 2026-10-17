package com.zoomers.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.designsystem.component.ErrorState
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.feature.home.PostCard

/** Full-screen view of a single post, opened by tapping a profile-grid tile. */
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    viewModel: PostDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    LaunchedEffect(postId) { viewModel.load(postId) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.padding(Spacing.sm).clickable(onClick = onBack),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text(
                "Post",
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
                state.error != null -> ErrorState(
                    state.error!!,
                    onRetry = { viewModel.load(postId) },
                    modifier = Modifier.padding(top = Spacing.xxl),
                )
                state.post != null -> Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    PostCard(
                        post = state.post!!,
                        onLikeToggle = viewModel::toggleLike,
                        onSaveToggle = viewModel::toggleSave,
                        onOpenProfile = onOpenProfile,
                        onOpenComments = { /* comments screen — a later milestone */ },
                    )
                }
            }
        }
    }
}
