package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Body colors for the handheld-style shell drawn behind the on-screen controls
 * in portrait mode.
 *
 * All variants are light on purpose: the pad icons in LemuroidPadTheme are dark
 * and would not be readable on a dark body.
 */
enum class ShellStyle(
    val top: Color,
    val bottom: Color,
) {
    CREAM(Color(0xFFDDD8CE), Color(0xFFC4BEB2)),
    LAVENDER(Color(0xFFD6D0EE), Color(0xFFB9B1DC)),
    MINT(Color(0xFFCFE3D6), Color(0xFFB2CDBB)),
}

/**
 * The lower half of a handheld console: a rounded body with a soft drop shadow
 * and a thin highlight along the top edge. Drawn in the area that holds the
 * touch controls.
 */
@Composable
fun HandheldShellBody(
    modifier: Modifier = Modifier,
    style: ShellStyle = ShellStyle.CREAM,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier =
            modifier
                .shadow(elevation = 16.dp, shape = shape, clip = false)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
                .drawBehind {
                    val inset = 28.dp.toPx()
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(inset, stroke),
                        end = Offset(size.width - inset, stroke),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                },
    )
}

/**
 * The full handheld body used in portrait mode: it sits behind the game
 * screens and the touch controls, so the whole phone reads as one console
 * instead of a video area floating above a separate control plate.
 */
@Composable
fun HandheldShellFullBody(
    modifier: Modifier = Modifier,
    style: ShellStyle = ShellStyle.CREAM,
) {
    Box(
        modifier =
            modifier
                .background(Brush.verticalGradient(listOf(style.top, style.bottom))),
    )
}

/**
 * Dark frame drawn around the game screens in portrait mode. The layout
 * insets the game view inside this frame, giving the screens the embedded
 * look of a real handheld.
 */
@Composable
fun HandheldScreenBezel(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier =
            modifier
                .shadow(elevation = 10.dp, shape = shape, clip = false)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2A2A30), Color(0xFF131316)),
                    ),
                )
                .border(
                    width = 2.dp,
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = shape,
                ),
    )
}
