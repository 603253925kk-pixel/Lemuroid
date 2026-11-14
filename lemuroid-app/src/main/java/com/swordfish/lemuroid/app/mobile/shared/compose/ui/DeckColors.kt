package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Original palette for the handheld-style UI: near-black navy surfaces,
 * a red accent and a red-to-purple gradient used for the selected nav item.
 */
object DeckColors {
    val Background = Color(0xFF0D0D14)
    val Surface = Color(0xFF16161F)
    val SurfaceRaised = Color(0xFF1E1E2A)
    val Outline = Color(0xFF2C2C3A)

    val Accent = Color(0xFFE5202E)
    val AccentPurple = Color(0xFF9B4DCA)

    val OnSurface = Color(0xFFF2F2F7)
    val OnSurfaceMuted = Color(0xFF9A9AAE)

    val AccentGradient: Brush =
        Brush.horizontalGradient(listOf(Accent, AccentPurple))

    // Per-category icon colors for the settings list.
    val IconBlue = Color(0xFF4C8DFF)
    val IconOrange = Color(0xFFFF9F0A)
    val IconGreen = Color(0xFF32D74B)
    val IconPink = Color(0xFFFF5FD2)
    val IconPurple = Color(0xFF7D6BFF)
    val IconTeal = Color(0xFF40D0C0)
    val IconRed = Color(0xFFFF453A)
}
