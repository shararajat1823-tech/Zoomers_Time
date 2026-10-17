package com.zoomers.app.feature.comments

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.core.common.relativeTime
import com.zoomers.app.core.model.CommentUi
import com.zoomers.app.designsystem.component.EmptyState
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun CommentsScreen(
    postId: String,
    onBack: () -> Unit,
    viewModel: CommentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZoomersTheme.colors

    LaunchedEffect(postId) { viewModel.load(postId) }

    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(Spacing.sm).clickable(onClick = onBack)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Comments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text, modifier = Modifier.padding(start = Spacing.sm))
        }

        // List
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = colors.textMuted)
                }
                state.comments.isEmpty() -> EmptyState("No comments yet", "Be the first to comment.", modifier = Modifier.padding(top = Spacing.xxl))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.comments, key = { it.id }) { CommentRow(it) }
                }
            }
        }

        if (state.error != null) {
            Text(state.error!!, color = colors.like, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs))
        }

        // Input
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a comment…") },
                maxLines = 4,
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
private fun CommentRow(comment: CommentUi) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        ZAvatar(key = comment.username, size = 36.dp)
        Column(Modifier.padding(start = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(comment.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.text)
                Text(relativeTime(comment.createdAtIso), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
    }
}
