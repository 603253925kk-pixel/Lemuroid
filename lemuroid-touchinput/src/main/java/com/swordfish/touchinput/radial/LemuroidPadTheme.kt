package com.swordfish.touchinput.radial

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class LemuroidPadTheme(
    /**
     * Optional replacement for the level1 fill (the disc behind the primary
     * dials). Null keeps the stock translucent glass. The portrait handheld
     * shell passes a dark tint so the dials sit in a recessed dark socket on
     * the body instead of on a light glass disc; every other consumer keeps
     * the default look.
     */
    level1FillOverride: Color? = null,
    /**
     * Optional replacements for the key face fill (level3) and the
     * letter/icon tint, used by the melonDS shell's per-game shell themes
     * (DS Lite look: light glossy keys with dark letters on the pale
     * shells, dark keys with light letters on the midnight shell). Null
     * keeps the stock light keys / dark letters. Pressed states are
     * untouched (dark fill + light icons), so a pressed key still reads
     * as pressed on every theme.
     */
    level3FillOverride: Color? = null,
    iconsOverride: Color? = null,
    /**
     * Optional per-letter colors for the face buttons, keyed by the
     * button's label ("A", "B", "X", "Y"). SNES / New 3DS style: X blue,
     * Y green, A red, B yellow. The melonDS shell passes
     * [ShellColorTheme.ABXY_LETTER_COLORS]; null keeps the single
     * [icons] tint for every letter (every other consumer). A mapped
     * letter keeps its color pressed too — the colors are the buttons'
     * identity, and they stay readable on the dark pressed fill.
     */
    faceButtonLetterColors: Map<String, Color>? = null,
) {
    private fun gray(
        luminosity: Float,
        opacity: Float,
    ): Color {
        return Color(luminosity, luminosity, luminosity, opacity)
    }

    val foregroundPadding: Dp = 8.dp
    val padding: Dp = 4.dp

    private val icons = iconsOverride ?: gray(0.0f, 0.50f)
    private val iconsPressed = gray(1.0f, 0.50f)

    // Constructor params are not properties, so the letter map is stored
    // here for letterColor() (member functions cannot see plain params).
    private val letterColors = faceButtonLetterColors

    private val level3Fill = level3FillOverride ?: gray(1.0f, 0.50f)
    private val level3FillPressed = gray(0.0f, 0.50f)
    val level3Shadow = DefaultShadowColor.copy(0.05f)
    val level3ShadowWidth = 4.dp

    private val level2Fill = gray(1.0f, 0.125f)
    private val level2FillPressed = gray(0.0f, 0.125f)
    val level2Shadow = DefaultShadowColor.copy(0.05f)
    val level2ShadowWidth = 4.dp

    val level1Fill = level1FillOverride ?: gray(1.0f, 0.10f)
    val level1Shadow = DefaultShadowColor.copy(0.10f)
    val level1ShadowWidth = 4.dp

    val level0CornerRadius = 0.dp
    val level0Fill = gray(1.0f, 0.05f)
    val level0Shadow = DefaultShadowColor.copy(0.10f)
    val level0ShadowWidth = 2.dp

    fun compositeFill(pressed: Boolean): Color {
        return if (pressed) {
            level2FillPressed
        } else {
            level2Fill
        }
    }

    fun foregroundFill(pressed: Boolean): Color {
        return if (pressed) {
            level3FillPressed
        } else {
            level3Fill
        }
    }

    fun icons(pressed: Boolean): Color {
        return if (pressed) {
            iconsPressed
        } else {
            icons
        }
    }

    /**
     * Letter color for a face button: the mapped SNES-style color when
     * the label is in [faceButtonLetterColors], else the plain [icons]
     * tint. A null label is never a face button.
     */
    fun letterColor(label: String?, pressed: Boolean): Color {
        return label?.let { letterColors?.get(it) } ?: icons(pressed)
    }
}

val LocalLemuroidPadTheme =
    compositionLocalOf<LemuroidPadTheme> {
        error("LemuroidPadTheme is missing")
    }
