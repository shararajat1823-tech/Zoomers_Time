package com.zoomers.app.core.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.zoomers.app.designsystem.theme.ZoomersTheme
import java.time.Duration
import java.time.Instant

/** Compact relative timestamp (e.g. "3h", "2d") from an ISO-8601 instant. */
fun relativeTime(iso: String, now: Instant = Instant.now()): String {
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val d = Duration.between(then, now)
    val seconds = d.seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}

/** Compact large counts: 2418 -> "2,418"; 12400 -> "12.4K". */
fun formatCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> {
        val k = n / 1000.0
        if (k >= 10) "${k.toInt()}K" else String.format("%.1fK", k)
    }
    else -> String.format("%.1fM", n / 1_000_000.0)
}

/**
 * Renders a caption with #hashtags and @mentions tinted in the accent color,
 * matching the concept where tags read as links.
 */
@Composable
fun captionAnnotated(username: String?, caption: String?): AnnotatedString {
    val colors = ZoomersTheme.colors
    val boldName = MaterialTheme.typography.titleMedium.toSpanStyle().copy(color = colors.text)
    return buildAnnotatedString {
        if (!username.isNullOrBlank()) {
            withStyle(boldName) { append(username) }
            append("  ")
        }
        val text = caption ?: ""
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '#' || c == '@') {
                var j = i + 1
                while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '.')) j++
                withStyle(SpanStyle(color = colors.accent)) { append(text.substring(i, j)) }
                i = j
            } else {
                append(c)
                i++
            }
        }
    }
}
