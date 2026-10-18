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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zoomers.app.designsystem.theme.StoryGradient
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

/**
 * Circular avatar. Falls back to a neutral tint (keyed by username) when no image
 * is available. With [hasStoryRing], draws the brand gradient ring with a thin
 * background gap between the ring and the photo — the familiar "unseen story" look.
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
        Modifier
            .border(2.5.dp, Brush.sweepGradient(StoryGradient), CircleShape)
            .padding(2.dp)
            .border(2.dp, colors.background, CircleShape)
            .padding(2.dp)
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
