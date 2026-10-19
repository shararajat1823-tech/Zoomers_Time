package com.zoomers.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.core.common.relativeTime
import com.zoomers.app.core.model.ConversationUi
import com.zoomers.app.designsystem.component.EmptyState
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenChat: (userId: String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.conversations.size - 3 && state.conversations.isNotEmpty()
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(Spacing.sm).clickable(onClick = onBack)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Messages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text, modifier = Modifier.padding(start = Spacing.sm))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> com.zoomers.app.designsystem.component.ListRowsSkeleton()
                state.error != null && state.conversations.isEmpty() ->
                    EmptyState("Couldn't load messages", state.error!!, modifier = Modifier.padding(top = Spacing.xxl))
                state.conversations.isEmpty() ->
                    EmptyState(
                        "No messages yet",
                        "Start a chat from someone's profile.",
                        modifier = Modifier.padding(top = Spacing.xxl),
                        icon = Icons.AutoMirrored.Outlined.Chat,
                    )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.id }) { c ->
                        ConversationRow(c, onClick = { onOpenChat(c.otherUserId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: ConversationUi, onClick: () -> Unit) {
    val colors = ZoomersTheme.colors
    val unread = c.unreadCount > 0
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZAvatar(key = c.username, size = 52.dp, imageUrl = c.avatarUrl)
        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(
                c.displayName.ifBlank { c.username },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                color = colors.text,
            )
            Text(
                c.lastMessagePreview ?: "Say hi 👋",
                style = MaterialTheme.typography.bodyMedium,
                color = if (unread) colors.text else colors.textMuted,
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(relativeTime(c.lastMessageAtIso), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            if (unread) {
                Box(
                    Modifier.padding(top = Spacing.xs).size(10.dp).clip(CircleShape).background(colors.primary),
                )
            }
        }
    }
}
