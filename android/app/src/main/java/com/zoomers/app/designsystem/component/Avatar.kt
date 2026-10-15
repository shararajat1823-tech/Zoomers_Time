package com.zoomers.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

/**
 * Circular avatar. Falls back to a stable pastel tint (keyed by username) when no
 * image URL is available, and can draw a teal "story ring" like the concept.
 */
@Composable
fun ZAvatar(
    key: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    imageUrl: String? = null,
    hasStoryRing: Boolean = false,
) {
    val colors = ZoomersTheme.colors
    val ringMod = if (hasStoryRing) {
        Modifier.border(2.dp, colors.storyRing, CircleShape).padding(3.dp)
    } else Modifier

    Box(modifier = modifier.then(ringMod), contentAlignment = Alignment.Center) {
        val inner = Modifier.size(size).clip(CircleShape).background(tintFor(key))
        if (imageUrl.isNullOrBlank()) {
            Box(inner)
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = inner,
            )
        }
    }
}
