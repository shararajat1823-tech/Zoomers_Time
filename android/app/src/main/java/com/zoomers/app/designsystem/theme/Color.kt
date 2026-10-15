package com.zoomers.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Zoomers palette, adapted from the "Lumi" concept: a warm cream canvas, deep
 * teal primary, coral for likes, and soft pastel category tints. Tokens are
 * defined once here and mapped to light/dark schemes in [Theme].
 */
internal object ZoomersPalette {
    // Brand
    val Teal = Color(0xFF1E7A6E)
    val TealLight = Color(0xFF7FD1C4)
    val TealBright = Color(0xFF2DD4BF) // dark-theme accent
    val Coral = Color(0xFFF0665A)

    // Light neutrals
    val Cream = Color(0xFFF4F2EA)
    val CreamElevated = Color(0xFFFBFAF4)
    val Ink = Color(0xFF17201D)
    val InkMuted = Color(0xFF6B726E)
    val HairlineLight = Color(0xFFE4E1D6)

    // Dark neutrals
    val Night = Color(0xFF0F1A17)
    val NightElevated = Color(0xFF16231F)
    val Mist = Color(0xFFEDEFEC)
    val MistMuted = Color(0xFF8C948F)
    val HairlineDark = Color(0xFF26332E)

    // Pastel category tints (explore grid, avatars placeholders)
    val Mint = Color(0xFFBFE0D6)
    val Peach = Color(0xFFF2C9B0)
    val Lavender = Color(0xFFD7D2E8)
    val Butter = Color(0xFFF2DFB0)
    val SkyBlue = Color(0xFFC3D3E8)
    val Blush = Color(0xFFEFCBD1)
}

/** Pastel tints cycled for placeholder avatars / explore cells, keyed by a stable id. */
val CategoryTints = listOf(
    ZoomersPalette.Mint,
    ZoomersPalette.Peach,
    ZoomersPalette.Lavender,
    ZoomersPalette.Butter,
    ZoomersPalette.SkyBlue,
    ZoomersPalette.Blush,
)

fun tintFor(key: Any?): Color {
    val h = (key?.hashCode() ?: 0)
    val idx = ((h % CategoryTints.size) + CategoryTints.size) % CategoryTints.size
    return CategoryTints[idx]
}
