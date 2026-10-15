package com.zoomers.app.designsystem.foundation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zoomers.app.designsystem.theme.Spacing

/**
 * Resolved layout decisions for the current window size. Centralizing these keeps
 * every screen responsive by construction: phones get a bottom bar and full-bleed
 * content, tablets/foldables/desktops get a side navigation rail and centered,
 * width-capped content with denser grids.
 */
data class ZoomersLayout(
    val useNavigationRail: Boolean,
    val contentMaxWidth: Dp,
    val horizontalGutter: Dp,
    val gridColumns: Int,
    val feedItemMaxWidth: Dp,
    val isCompact: Boolean,
)

@Composable
fun rememberZoomersLayout(windowSizeClass: WindowSizeClass): ZoomersLayout {
    val width = windowSizeClass.widthSizeClass
    return remember(width) {
        when (width) {
            WindowWidthSizeClass.Compact -> ZoomersLayout(
                useNavigationRail = false,
                contentMaxWidth = Dp.Unspecified,
                horizontalGutter = Spacing.lg,
                gridColumns = 3,
                feedItemMaxWidth = Dp.Unspecified,
                isCompact = true,
            )
            WindowWidthSizeClass.Medium -> ZoomersLayout(
                useNavigationRail = true,
                contentMaxWidth = 640.dp,
                horizontalGutter = Spacing.xl,
                gridColumns = 4,
                feedItemMaxWidth = 560.dp,
                isCompact = false,
            )
            else -> ZoomersLayout( // Expanded: tablets, foldables unfolded, desktop
                useNavigationRail = true,
                contentMaxWidth = 960.dp,
                horizontalGutter = Spacing.xxl,
                gridColumns = 6,
                feedItemMaxWidth = 600.dp,
                isCompact = false,
            )
        }
    }
}
