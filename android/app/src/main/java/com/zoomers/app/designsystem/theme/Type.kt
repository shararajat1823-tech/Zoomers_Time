package com.zoomers.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale. The brand wordmark uses a serif; UI text uses the platform sans.
 * (Swap [BrandSerif] for a bundled font such as Playfair/DM Serif Display to
 * match the concept exactly — kept as the system serif here to avoid shipping
 * binary font assets in this starter.)
 */
val BrandSerif = FontFamily.Serif
val UiSans = FontFamily.SansSerif

internal fun zoomersTypography(colors: ZoomersColors) = Typography(
    // Brand wordmark / large display
    displaySmall = TextStyle(fontFamily = BrandSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = colors.text),
    headlineMedium = TextStyle(fontFamily = BrandSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = colors.text),

    titleLarge = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.text),
    titleMedium = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.text),
    titleSmall = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.text),

    bodyLarge = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp, color = colors.text),
    bodyMedium = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = colors.text),
    bodySmall = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = colors.textMuted),

    labelLarge = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.text),
    labelMedium = TextStyle(fontFamily = UiSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.textMuted),
)
