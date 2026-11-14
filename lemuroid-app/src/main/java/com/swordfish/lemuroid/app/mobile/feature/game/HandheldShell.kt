package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central palette of the portrait handheld shell: dark charcoal body in the
 * style of a real handheld console, near-black screen frames and subtle
 * light-gray details. Everything shell-related (body, bezel, hinge band,
 * speaker grille, fast-forward button) reads its colors from here.
 */
object ShellTheme {
    val bodyTop = Color(0xFF333338)
    val bodyBottom = Color(0xFF26262B)
    val bezelTop = Color(0xFF1B1B1E)
    val bezelBottom = Color(0xFF141416)
    val bezelBorder = Color.Black.copy(alpha = 0.6f)
    val detailLabel = Color(0xFFC9C9D1)
    val grilleDot = Color(0xFF131315)
    val fastForwardInactive = Color(0xFF3A3A41)
    val fastForwardActive = Color(0xFFB7A6E8)
    val fastForwardLabel = Color(0xFFC9C9D1)
}

/**
 * Body colors for the handheld-style shell drawn behind the on-screen controls
 * in portrait mode.
 */
enum class ShellStyle(
    val top: Color,
    val bottom: Color,
) {
    CHARCOAL(ShellTheme.bodyTop, ShellTheme.bodyBottom),
    CREAM(Color(0xFFDDD8CE), Color(0xFFC4BEB2)),
    LAVENDER(Color(0xFFD6D0EE), Color(0xFFB9B1DC)),
    MINT(Color(0xFFCFE3D6), Color(0xFFB2CDBB)),
}

/**
 * The lower half of a handheld console: a rounded body plate with a soft drop
 * shadow and a thin highlight along the top edge. Drawn in the area that holds
 * the touch controls; on the one-piece shell it reads as the seam between the
 * screen half and the control half of the body.
 */
@Composable
fun HandheldShellBody(
    modifier: Modifier = Modifier,
    style: ShellStyle = ShellStyle.CHARCOAL,
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
 * Fills this layer's bounds with [brush], leaving transparent openings where
 * the game screens sit.
 *
 * The game renders on a surface *below* this Compose overlay, confined to the
 * viewport rect the layout reports in root coordinates. Any opaque paint over
 * that rect hides the game completely (this was the portrait black-screen
 * bug), so the shell layers must never fill it: the fill is drawn as an
 * even-odd path (outer rounded rect minus the screen openings).
 *
 * In dual-screen mode the viewport holds both DS screens stacked by the core,
 * with the seam at the viewport's vertical center (the surface centers the
 * content). Two openings are cut instead of one, leaving a body strip — the
 * hinge band — across the seam. When [cutBand] is set (the bezel layer), the
 * band strip is cut out of this layer as well, so the body layer below shows
 * through and the band reads as part of the body, not of the frame.
 *
 * While the positions are still unknown (first frames, game still loading)
 * the layer is filled solid, exactly like the previous look.
 *
 * [screenRect] is the game viewport in root coordinates (see
 * `Modifier.boundsInRoot`); [ownBounds] is this layer's own root bounds, used
 * to convert the openings into local coordinates.
 */
private fun DrawScope.drawShellFill(
    brush: Brush,
    outerCornerRadiusPx: Float,
    screenRect: Rect?,
    ownBounds: Rect?,
    holeCornerRadiusPx: Float,
    dualScreen: Boolean = false,
    bandHeightPx: Float = 0f,
    cutBand: Boolean = false,
) {
    if (screenRect == null || ownBounds == null) {
        if (outerCornerRadiusPx > 0f) {
            drawRoundRect(brush = brush, cornerRadius = CornerRadius(outerCornerRadiusPx))
        } else {
            drawRect(brush = brush)
        }
        return
    }

    val window =
        Rect(
            left = (screenRect.left - ownBounds.left).coerceIn(0f, size.width),
            top = (screenRect.top - ownBounds.top).coerceIn(0f, size.height),
            right = (screenRect.right - ownBounds.left).coerceIn(0f, size.width),
            bottom = (screenRect.bottom - ownBounds.top).coerceIn(0f, size.height),
        )

    val holes = mutableListOf<RoundRect>()
    if (window.width > 0f && window.height > 0f) {
        if (dualScreen && bandHeightPx > 0f && window.height > bandHeightPx) {
            val midY = (window.top + window.bottom) / 2f
            val halfBand = bandHeightPx / 2f
            holes.add(
                RoundRect(
                    left = window.left,
                    top = window.top,
                    right = window.right,
                    bottom = midY - halfBand,
                    cornerRadius = CornerRadius(holeCornerRadiusPx),
                ),
            )
            holes.add(
                RoundRect(
                    left = window.left,
                    top = midY + halfBand,
                    right = window.right,
                    bottom = window.bottom,
                    cornerRadius = CornerRadius(holeCornerRadiusPx),
                ),
            )
            if (cutBand) {
                holes.add(
                    RoundRect(
                        left = 0f,
                        top = midY - halfBand,
                        right = size.width,
                        bottom = midY + halfBand,
                        cornerRadius = CornerRadius(0f),
                    ),
                )
            }
        } else {
            holes.add(
                RoundRect(
                    left = window.left,
                    top = window.top,
                    right = window.right,
                    bottom = window.bottom,
                    cornerRadius = CornerRadius(holeCornerRadiusPx),
                ),
            )
        }
    }

    val path =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(outerCornerRadiusPx),
                ),
            )
            holes.forEach { addRoundRect(it) }
        }
    drawPath(path = path, brush = brush)
}

/**
 * The full handheld body used in portrait mode: one integrated shell behind
 * the game screens and the touch controls, so the whole phone reads as a
 * single console instead of a video area floating above a separate control
 * plate.
 *
 * [screenRect] is the game viewport in root coordinates; the body stays
 * transparent at the screen windows so the game surface below shows through.
 * In dual-screen mode the strip between the two windows — the hinge band —
 * remains filled with the body color.
 */
@Composable
fun HandheldShellFullBody(
    modifier: Modifier = Modifier,
    style: ShellStyle = ShellStyle.CHARCOAL,
    screenRect: Rect? = null,
    dualScreen: Boolean = false,
) {
    var ownBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier =
            modifier
                .onGloballyPositioned { ownBounds = it.boundsInRoot() }
                .drawWithContent {
                    drawShellFill(
                        brush = Brush.verticalGradient(listOf(style.top, style.bottom)),
                        outerCornerRadiusPx = 0f,
                        screenRect = screenRect,
                        ownBounds = ownBounds,
                        holeCornerRadiusPx = 10.dp.toPx(),
                        dualScreen = dualScreen,
                        bandHeightPx = GameScreenLayout.HINGE_BAND_HEIGHT.toPx(),
                    )
                    drawContent()
                },
    )
}

/**
 * Dark frame drawn around the game screens in portrait mode, like the frame
 * of a real handheld: each screen sits in its own window cut into the frame.
 *
 * This is a frame, not a panel: the fill leaves an opening exactly at each
 * screen (see [drawShellFill]), so only the frame around the screens is
 * painted and the game below stays visible. In dual-screen mode the hinge
 * band between the windows is also left unpainted here, so the body color
 * shows through as the hinge.
 */
@Composable
fun HandheldScreenBezel(
    modifier: Modifier = Modifier,
    screenRect: Rect? = null,
    dualScreen: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    var ownBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier =
            modifier
                .shadow(elevation = 10.dp, shape = shape, clip = false)
                .clip(shape)
                .onGloballyPositioned { ownBounds = it.boundsInRoot() }
                .drawWithContent {
                    drawShellFill(
                        brush =
                            Brush.verticalGradient(
                                listOf(ShellTheme.bezelTop, ShellTheme.bezelBottom),
                            ),
                        outerCornerRadiusPx = 20.dp.toPx(),
                        screenRect = screenRect,
                        ownBounds = ownBounds,
                        holeCornerRadiusPx = 10.dp.toPx(),
                        dualScreen = dualScreen,
                        bandHeightPx = GameScreenLayout.HINGE_BAND_HEIGHT.toPx(),
                        cutBand = true,
                    )
                    drawContent()
                }
                .border(
                    width = 2.dp,
                    color = ShellTheme.bezelBorder,
                    shape = shape,
                ),
    )
}

@Composable
private fun SpeakerDots(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) {
            Box(
                modifier =
                    Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(ShellTheme.grilleDot),
            )
        }
    }
}

/**
 * Decorative details sitting on the hinge band between the two DS screens:
 * small L/R marks at the ends and a speaker-dot row in the middle. The band
 * itself is painted by the body layer underneath; this composable is
 * transparent and only carries the details. It is laid out centered on the
 * seam between the screens, with the same height the shell fill leaves for
 * the band, so the details stay inside the strip.
 */
@Composable
fun HandheldHingeBand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "L",
            color = ShellTheme.detailLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        SpeakerDots(count = 5)
        Text(
            text = "R",
            color = ShellTheme.detailLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Small speaker grille for single-screen shells (GBA): two rows of dots on
 * the body below the screen, echoing the hinge band of the dual-screen body
 * so both shells share the same design language.
 */
@Composable
fun HandheldSpeakerGrille(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpeakerDots(count = 6)
        SpeakerDots(count = 6)
    }
}
