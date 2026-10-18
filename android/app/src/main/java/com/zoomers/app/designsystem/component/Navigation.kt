package com.zoomers.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zoomers.app.designsystem.theme.Shapes
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme
import com.zoomers.app.designsystem.theme.tintFor

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val isCreate: Boolean = false,
    val isProfile: Boolean = false,
    val avatarUrl: String? = null,
    val avatarKey: String? = null,
)

/** Bottom navigation bar for phones. The center "create" item is a filled teal tile. */
@Composable
fun ZBottomBar(
    items: List<NavItem>,
    currentRoute: String?,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawTopHairline(colors.hairline)
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            NavCell(item = item, selected = item.route == currentRoute, onClick = { onSelect(item) }, modifier = Modifier.size(width = 56.dp, height = 48.dp))
        }
    }
}

/** Side navigation rail for tablets / foldables / landscape. */
@Composable
fun ZNavRail(
    items: List<NavItem>,
    currentRoute: String?,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZoomersTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.surface)
            .systemBarsPadding()
            .padding(horizontal = Spacing.sm, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEach { item ->
            NavCell(item = item, selected = item.route == currentRoute, onClick = { onSelect(item) }, modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun NavCell(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZoomersTheme.colors
    Box(
        modifier = modifier.clip(Shapes.button).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Profile tab shows the signed-in user's avatar, ringed when selected.
            item.isProfile -> {
                val ring = if (selected) Modifier.border(1.5.dp, colors.text, CircleShape).padding(2.dp) else Modifier
                Box(ring) {
                    AvatarDot(item.avatarUrl, item.avatarKey ?: item.route, size = if (selected) 26.dp else 28.dp)
                }
            }
            // Create is a plain outline/filled icon (no colored tile).
            else -> Icon(
                if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = colors.text,
                modifier = Modifier.size(if (item.isCreate) 28.dp else 26.dp),
            )
        }
    }
}

@Composable
private fun AvatarDot(imageUrl: String?, key: Any?, size: androidx.compose.ui.unit.Dp) {
    val inner = Modifier.size(size).clip(CircleShape).background(tintFor(key))
    if (imageUrl.isNullOrBlank()) {
        Box(inner)
    } else {
        AsyncImage(model = imageUrl, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = inner)
    }
}

/** Draws a 1px hairline across the top edge (the familiar bottom-bar divider). */
private fun Modifier.drawTopHairline(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(
        androidx.compose.ui.draw.drawBehind {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = 1f,
            )
        },
    )
