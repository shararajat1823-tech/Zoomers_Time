package com.zoomers.app.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Semantic color tokens for Zoomers. Screens and components read these instead of
 * raw palette values, so light/dark and future re-skins stay centralized.
 */
data class ZoomersColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val text: Color,
    val textMuted: Color,
    val hairline: Color,
    val like: Color,
    val storyRing: Color,
    val isDark: Boolean,
)

private val LightColors = ZoomersColors(
    background = ZoomersPalette.White,
    surface = ZoomersPalette.White,
    surfaceElevated = ZoomersPalette.OffWhite,
    primary = ZoomersPalette.ActionBlue,
    onPrimary = ZoomersPalette.White,
    accent = ZoomersPalette.ActionBlue,
    text = ZoomersPalette.Ink,
    textMuted = ZoomersPalette.InkMuted,
    hairline = ZoomersPalette.HairlineLight,
    like = ZoomersPalette.LikeRed,
    storyRing = ZoomersPalette.G3,
    isDark = false,
)

private val DarkColors = ZoomersColors(
    background = ZoomersPalette.Black,
    surface = ZoomersPalette.Black,
    surfaceElevated = ZoomersPalette.BlackElevated,
    primary = ZoomersPalette.ActionBlueDark,
    onPrimary = ZoomersPalette.White,
    accent = ZoomersPalette.ActionBlueDark,
    text = ZoomersPalette.Mist,
    textMuted = ZoomersPalette.MistMuted,
    hairline = ZoomersPalette.HairlineDark,
    like = ZoomersPalette.LikeRed,
    storyRing = ZoomersPalette.G3,
    isDark = true,
)

val LocalZoomersColors = staticCompositionLocalOf { LightColors }

/** Convenience accessor: `ZoomersTheme.colors`, `ZoomersTheme.typography`, etc. */
object ZoomersTheme {
    val colors: ZoomersColors
        @Composable get() = LocalZoomersColors.current
}

@Composable
fun ZoomersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Bridge to a Material3 scheme so Material components inherit the brand.
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            error = colors.like,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            error = colors.like,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalZoomersColors provides colors) {
        MaterialTheme(
            colorScheme = material,
            typography = zoomersTypography(colors),
            content = content,
        )
    }
}
