package com.zoomers.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zoomers.app.core.model.PostUi
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

/** A simple back + title header used by secondary full-screen destinations. */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(Spacing.sm).clickable(onClick = onBack)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text, modifier = Modifier.padding(start = Spacing.sm))
    }
}

/** A square-tile grid of posts (first media each), tap to open. */
@Composable
fun PostThumbGrid(
    posts: List<PostUi>,
    gridColumns: Int,
    onOpenPost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZoomersTheme.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = modifier.fillMaxSize().widthIn(max = 720.dp),
    ) {
        items(posts, key = { it.id }) { post ->
            val imageUrl = post.media.firstOrNull()?.let { it.thumbnailUrl ?: it.url }
            Box(
                Modifier.padding(1.dp).aspectRatio(1f).background(tintFor(post.id)).clickable { onOpenPost(post.id) },
            ) {
                if (imageUrl != null) {
                    AsyncImage(model = imageUrl, contentDescription = post.caption, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (post.isCarousel) {
                    Icon(Icons.Outlined.Collections, contentDescription = "Multiple photos", tint = colors.background, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                }
            }
        }
    }
}
