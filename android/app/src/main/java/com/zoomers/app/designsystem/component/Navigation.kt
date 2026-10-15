package com.zoomers.app.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zoomers.app.designsystem.theme.Shapes
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

data class NavItem(val route: String, val label: String, val icon: ImageVector, val isCreate: Boolean)

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
        if (item.isCreate) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = colors.onPrimary, modifier = Modifier.size(26.dp))
            }
        } else {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = if (selected) colors.text else colors.textMuted,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
