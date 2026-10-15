package com.zoomers.app.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zoomers.app.designsystem.theme.ZoomersTheme

/**
 * Segmented pill toggle (e.g. "Following" / "For you") exactly as in the concept:
 * a rounded track with a filled selected pill that slides between options.
 */
@Composable
fun ZPillTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZoomersTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceElevated)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) colors.text else androidx.compose.ui.graphics.Color.Transparent,
                label = "pillBg",
            )
            val fg by animateColorAsState(
                if (selected) colors.background else colors.textMuted,
                label = "pillFg",
            )
            Text(
                text = label,
                textAlign = TextAlign.Center,
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(index) }
                    .background(bg)
                    .padding(vertical = 10.dp),
            )
        }
    }
}
