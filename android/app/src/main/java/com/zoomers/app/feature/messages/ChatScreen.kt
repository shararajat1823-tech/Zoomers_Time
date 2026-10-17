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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.core.common.relativeTime
import com.zoomers.app.core.model.MessageUi
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun ChatScreen(
    peerId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(peerId) { viewModel.load(peerId) }

    // reverseLayout: the "end" of the list is the oldest message — load older there.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.messages.size - 3 && state.messages.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(Spacing.sm).clickable(onClick = onBack)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            ZAvatar(key = state.peerUsername, size = 34.dp, imageUrl = state.peerAvatarUrl)
            Column(Modifier.padding(start = Spacing.sm)) {
                Text(
                    state.peerDisplayName.ifBlank { state.peerUsername },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
                if (state.peerUsername.isNotBlank()) {
                    Text("@${state.peerUsername}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (state.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Say hello 👋", color = colors.textMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(state.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                }
            }
        }

        if (state.error != null) {
            Text(state.error!!, color = colors.like, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs))
        }

        // Composer
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.hairline,
                    cursorColor = colors.primary,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                ),
            )
            Box(
                modifier = Modifier
                    .padding(start = Spacing.sm)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = state.canSend) { viewModel.send() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (state.canSend) colors.primary else colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageUi) {
    val colors = ZoomersTheme.colors
    val bubbleColor = if (msg.isMine) colors.primary else colors.surfaceElevated
    val textColor = if (msg.isMine) colors.onPrimary else colors.text
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (msg.isMine) 18.dp else 4.dp,
        bottomEnd = if (msg.isMine) 4.dp else 18.dp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Text(msg.body, style = MaterialTheme.typography.bodyLarge, color = textColor)
            Text(
                relativeTime(msg.createdAtIso),
                style = MaterialTheme.typography.labelSmall,
                color = if (msg.isMine) colors.onPrimary.copy(alpha = 0.7f) else colors.textMuted,
                modifier = Modifier.padding(top = 2.dp).align(Alignment.End),
            )
        }
    }
}
