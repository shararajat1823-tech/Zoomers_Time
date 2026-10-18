package com.zoomers.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale. A clean platform sans-serif is used everywhere for a crisp,
 * modern social feel. [BrandSerif] is retained as an alias (also sans) so any
 * remaining references keep compiling.
 */
val UiSans = FontFamily.SansSerif
val BrandSerif = UiSans

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
