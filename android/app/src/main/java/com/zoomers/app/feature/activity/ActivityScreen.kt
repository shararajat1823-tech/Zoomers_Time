package com.zoomers.app.feature.activity

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.zoomers.app.core.common.relativeTime
import com.zoomers.app.core.model.ActivityType
import com.zoomers.app.core.model.NotificationUi
import com.zoomers.app.designsystem.component.EmptyState
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(Spacing.sm).clickable(onClick = onBack)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text, modifier = Modifier.padding(start = Spacing.sm))
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = colors.textMuted)
                }
                state.items.isEmpty() -> EmptyState("No activity yet", "Likes, comments and new followers show up here.", modifier = Modifier.padding(top = Spacing.xxl))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { n ->
                        ActivityRow(
                            n = n,
                            onClick = {
                                if (n.postId != null) onOpenPost(n.postId) else onOpenUser(n.username)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(n: NotificationUi, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZAvatar(key = n.username, size = 44.dp, imageUrl = n.avatarUrl)
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                buildString {
                    append(n.username)
                    append(' ')
                    append(verbFor(n.type))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
            Text(relativeTime(n.createdAtIso), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (n.postThumbnailUrl != null) {
            AsyncImage(
                model = n.postThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(tintFor(n.id)),
            )
        }
    }
}

private fun verbFor(type: ActivityType): String = when (type) {
    ActivityType.Like -> "liked your post."
    ActivityType.Comment -> "commented on your post."
    ActivityType.Follow -> "started following you."
    ActivityType.FollowRequest -> "requested to follow you."
    ActivityType.Unknown -> "interacted with you."
}
