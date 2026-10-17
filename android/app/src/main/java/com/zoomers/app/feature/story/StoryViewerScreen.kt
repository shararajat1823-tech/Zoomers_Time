package com.zoomers.app.feature.story

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zoomers.app.core.common.relativeTime
import com.zoomers.app.designsystem.component.ZAvatar
import com.zoomers.app.designsystem.theme.Spacing

private const val StoryDurationMs = 5000

@Composable
fun StoryViewerScreen(
    startAuthorId: String,
    onClose: () -> Unit,
    viewModel: StoryViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(startAuthorId) { viewModel.load(startAuthorId) }
    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    val progress = remember { Animatable(0f) }
    // Restart the timer whenever the shown story changes; advance when it fills.
    LaunchedEffect(state.authorIndex, state.storyIndex, state.story?.id) {
        if (state.story == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(StoryDurationMs, easing = LinearEasing))
        viewModel.next()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = constraints.maxWidth
        val story = state.story
        val tray = state.tray

        if (story != null) {
            AsyncImage(
                model = story.mediaUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Tap zones: left third = previous, rest = next.
        Box(
            Modifier.fillMaxSize().pointerInput(state.authorIndex, state.storyIndex) {
                detectTapGestures { offset ->
                    if (offset.x < widthPx / 3f) viewModel.prev() else viewModel.next()
                }
            },
        )

        // Top overlay: progress bars + author header.
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val count = tray?.stories?.size ?: 0
                for (i in 0 until count) {
                    val p = when {
                        i < state.storyIndex -> 1f
                        i == state.storyIndex -> progress.value
                        else -> 0f
                    }
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)),
                    ) {
                        Box(Modifier.fillMaxWidth(p).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZAvatar(key = tray?.username ?: "", size = 34.dp, imageUrl = tray?.avatarUrl)
                Spacer(Modifier.width(Spacing.sm))
                Text(tray?.username ?: "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.width(Spacing.sm))
                story?.let {
                    Text(relativeTime(it.createdAtIso), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
