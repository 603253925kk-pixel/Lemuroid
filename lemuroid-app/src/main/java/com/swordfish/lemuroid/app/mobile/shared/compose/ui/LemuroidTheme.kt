package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Pixel-style Material You fallback palettes (static, for Android < 12):
 * solid matte tonal blocks on the M3 baseline blue seed (#0B57D0 family) —
 * the same hues the approved Pixel mockup uses. On Android 12+ the system
 * dynamic colors (wallpaper-derived) take over instead.
 */
private val PixelLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF0B57D0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E3FD),
        onPrimaryContainer = Color(0xFF001D35),
        secondary = Color(0xFF565F71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF131C2B),
        tertiary = Color(0xFF705575),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFAD8FD),
        onTertiaryContainer = Color(0xFF28132E),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color(0xFFFFFFFF),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFEFBFF),
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFFEFBFF),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = Color(0xFFE1E2EC),
        onSurfaceVariant = Color(0xFF44474E),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F2FA),
        surfaceContainer = Color(0xFFF2F0F4),
        surfaceContainerHigh = Color(0xFFECE6F0),
        surfaceContainerHighest = Color(0xFFE6E0E9),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        inverseOnSurface = Color(0xFFF2F0F4),
        inverseSurface = Color(0xFF2F3033),
        inversePrimary = Color(0xFFA8C7FA),
        surfaceTint = Color(0xFF0B57D0),
        scrim = Color(0xFF000000),
    )

private val PixelDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA8C7FA),
        onPrimary = Color(0xFF00315C),
        primaryContainer = Color(0xFF0842A0),
        onPrimaryContainer = Color(0xFFD3E3FD),
        secondary = Color(0xFFBEC6DC),
        onSecondary = Color(0xFF283041),
        secondaryContainer = Color(0xFF3E4759),
        onSecondaryContainer = Color(0xFFDAE2F9),
        tertiary = Color(0xFFE7B8E8),
        onTertiary = Color(0xFF3E2846),
        tertiaryContainer = Color(0xFF573D5B),
        onTertiaryContainer = Color(0xFFFAD8FD),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF131316),
        onBackground = Color(0xFFE3E2E6),
        surface = Color(0xFF131316),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = Color(0xFF44474E),
        onSurfaceVariant = Color(0xFFC4C6D0),
        surfaceContainerLowest = Color(0xFF0E0E11),
        surfaceContainerLow = Color(0xFF1B1B1F),
        surfaceContainer = Color(0xFF1F1F23),
        surfaceContainerHigh = Color(0xFF2A2A2E),
        surfaceContainerHighest = Color(0xFF353539),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474E),
        inverseOnSurface = Color(0xFF2F3033),
        inverseSurface = Color(0xFFE3E2E6),
        inversePrimary = Color(0xFF0B57D0),
        surfaceTint = Color(0xFFA8C7FA),
        scrim = Color(0xFF000000),
    )

@Composable
fun AppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Pixel-style Material You: on Android 12+ the colors come from the
    // system (wallpaper-derived dynamic scheme); below that the static
    // Pixel tonal palettes above are the fallback. The old static
    // md_theme_* schemes and the custom Deck dark palette are retired by
    // this change — the handheld game shell keeps its own charcoal
    // ShellTheme and is unaffected.
    val colors =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> PixelDarkColorScheme
            else -> PixelLightColorScheme
        }

    MaterialTheme(colorScheme = colors) {
        content()
    }
}
