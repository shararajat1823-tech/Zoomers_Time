package com.zoomers.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Zoomers palette. A clean, high-contrast social look: white / true-black
 * canvases, neutral grays, a blue action accent, and red likes — with a warm
 * magenta→orange gradient reserved for story rings and the wordmark. Tokens are
 * defined once here and mapped to light/dark schemes in [Theme].
 */
internal object ZoomersPalette {
    // Accents
    val ActionBlue = Color(0xFF0095F6)   // buttons, links, selected accents
    val ActionBlueDark = Color(0xFF1F9BF0)
    val LikeRed = Color(0xFFFF3040)      // hearts

    // Light neutrals
    val White = Color(0xFFFFFFFF)
    val OffWhite = Color(0xFFFAFAFA)
    val Ink = Color(0xFF121212)
    val InkMuted = Color(0xFF8E8E8E)
    val HairlineLight = Color(0xFFDBDBDB)

    // Dark neutrals — a deep navy canvas with elevated card surfaces.
    val Black = Color(0xFF0B0E17)
    val BlackElevated = Color(0xFF171D2E)
    val Mist = Color(0xFFF5F7FA)
    val MistMuted = Color(0xFF8A93A6)
    val HairlineDark = Color(0xFF232A3D)

    // Accent gradient (selected chips, create button): blue -> violet.
    val AccentBlue = Color(0xFF4D6CFE)
    val AccentViolet = Color(0xFF9B4DFF)

    // Story ring / brand gradient stops (warm → magenta → indigo).
    val G1 = Color(0xFFFEDA75)
    val G2 = Color(0xFFFA7E1E)
    val G3 = Color(0xFFD62976)
    val G4 = Color(0xFF962FBF)
    val G5 = Color(0xFF4F5BD5)

    // Neutral placeholder tints for empty avatars / media cells.
    val Gray1 = Color(0xFFEFEFEF)
    val Gray2 = Color(0xFFE4E4E4)
    val Gray3 = Color(0xFFD8D8D8)
    val Gray4 = Color(0xFFCDCDCD)
}

/** Accent gradient (blue -> violet) for selected chips and the create button. */
val AccentGradient = listOf(ZoomersPalette.AccentBlue, ZoomersPalette.AccentViolet)

/** Story-ring / brand gradient stops, used for a sweep/linear brush. */
val StoryGradient = listOf(
    ZoomersPalette.G1,
    ZoomersPalette.G2,
    ZoomersPalette.G3,
    ZoomersPalette.G4,
    ZoomersPalette.G5,
    ZoomersPalette.G1, // loop back so a sweep gradient joins cleanly
)

/** Neutral tints cycled for placeholder avatars / empty cells, keyed by a stable id. */
val CategoryTints = listOf(
    ZoomersPalette.Gray1,
    ZoomersPalette.Gray2,
    ZoomersPalette.Gray3,
    ZoomersPalette.Gray4,
)

fun tintFor(key: Any?): Color {
    val h = (key?.hashCode() ?: 0)
    val idx = ((h % CategoryTints.size) + CategoryTints.size) % CategoryTints.size
    return CategoryTints[idx]
}
