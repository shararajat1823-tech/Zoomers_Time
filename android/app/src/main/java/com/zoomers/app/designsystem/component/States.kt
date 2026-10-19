package com.zoomers.app.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

/** A shimmering placeholder block: a bright band sweeps across for a "loading" sheen. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, cornerRadius: Int = 12) {
    val base = ZoomersTheme.colors.hairline
    val transition = rememberInfiniteTransition(label = "skeleton")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "skeletonSweep",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.35f), base.copy(alpha = 0.9f), base.copy(alpha = 0.35f)),
        start = Offset(x, 0f),
        end = Offset(x + 420f, 0f),
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(brush),
    )
}

/** Shimmering placeholder grid for photo grids (explore, profiles) while loading. */
@Composable
fun PhotoGridSkeleton(columns: Int = 3, rows: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth()) {
                repeat(columns) {
                    SkeletonBox(
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp),
                        cornerRadius = 0,
                    )
                }
            }
        }
    }
}

/** Shimmering placeholder rows (avatar + two lines) for lists like the inbox. */
@Composable
fun ListRowsSkeleton(count: Int = 8, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(count) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.width(52.dp).height(52.dp), cornerRadius = 50)
                Column(Modifier.padding(start = Spacing.md)) {
                    SkeletonBox(Modifier.width(140.dp).height(13.dp))
                    Box(Modifier.height(Spacing.sm))
                    SkeletonBox(Modifier.width(220.dp).height(11.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = ZoomersTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(34.dp),
                )
            }
            Box(Modifier.height(Spacing.sm))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = ZoomersTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        ZSecondaryButton(text = "Try again", onClick = onRetry)
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val colors = ZoomersTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text("You're offline — showing cached content", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}
