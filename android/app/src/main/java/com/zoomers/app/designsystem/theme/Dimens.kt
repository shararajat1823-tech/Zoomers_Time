package com.zoomers.app.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing scale (4dp base) used throughout for consistent rhythm. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii matching the concept's soft, rounded language. */
object Shapes {
    val pill = RoundedCornerShape(50)
    val card = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(14.dp)
    val field = RoundedCornerShape(14.dp)
    val create = RoundedCornerShape(14.dp)
}
