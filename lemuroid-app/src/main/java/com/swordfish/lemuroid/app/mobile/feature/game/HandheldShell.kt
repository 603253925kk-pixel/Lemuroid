package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    val powerLed = Color(0xFF7ED491)
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
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(inset, stroke),
                        end = Offset(size.width - inset, stroke),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    // Soft shadow in the seam right below the top edge, so
                    // the deck reads as a separate plate of the body.
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                                startY = 0f,
                                endY = 18.dp.toPx(),
                            ),
                    )
                },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Power LED with its micro label, like on real handhelds: a small
        // lived detail on the deck, centered clear of the two pads.
        Row(
            modifier = Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ShellTheme.powerLed),
            )
            Text(
                text = "POWER",
                color = ShellTheme.detailLabel.copy(alpha = 0.75f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
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
    holeInflatePx: Float = 0f,
) {
    if (screenRect == null || ownBounds == null) {
        if (outerCornerRadiusPx > 0f) {
            drawRoundRect(brush = brush, cornerRadius = CornerRadius(outerCornerRadiusPx))
        } else {
            drawRect(brush = brush)
        }
        return
    }

    // The opening can be inflated by a few dp when another layer defines the
    // exact window edges (the geometry-driven screen frame): this base fill
    // then only has to stay out of the way, never to be pixel-exact.
    val window =
        Rect(
            left = (screenRect.left - ownBounds.left - holeInflatePx).coerceIn(0f, size.width),
            top = (screenRect.top - ownBounds.top - holeInflatePx).coerceIn(0f, size.height),
            right = (screenRect.right - ownBounds.left + holeInflatePx).coerceIn(0f, size.width),
            bottom = (screenRect.bottom - ownBounds.top + holeInflatePx).coerceIn(0f, size.height),
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
    holeInflate: Dp = 0.dp,
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
                        holeInflatePx = holeInflate.toPx(),
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

/** Which screen frame [GameViewportShellBox] draws around the viewport. */
enum class ShellFrameMode {
    /** No frame: generic shells keep the overlay layers, landscape none. */
    NONE,

    /** One screen window (GBA shell). */
    SINGLE,

    /** Two screen windows split by the hinge band (DS shell). */
    DUAL,
}

/**
 * The game viewport placeholder, with the handheld screen frame drawn in
 * its own draw pass for the geometry-driven shells.
 *
 * The placeholder is what the layout measures to position the GL viewport,
 * and this composable keeps the exact modifier chain that measurement has
 * always used (layout id, display-cutout padding, position callback), then
 * adds the frame drawing on the *same* layout node. The frame is therefore
 * painted in the very coordinate system the viewport is reported in: its
 * openings cannot drift from the rendered game, whatever insets the device
 * applies. The frame overhangs the placeholder on every side (drawing is
 * not clipped to the node bounds), forming the bezel around the content.
 *
 * In dual-screen mode the content rect holds both DS screens with the seam
 * at its vertical center; the frame leaves two openings and paints the
 * hinge band strip across the seam. The band details (L/R marks, speaker
 * dots) are a separate composable laid out over the same strip.
 */
@Composable
fun GameViewportShellBox(
    frameMode: ShellFrameMode,
    onPositioned: (Rect) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
                .drawWithContent {
                    if (frameMode != ShellFrameMode.NONE) {
                        drawHandheldScreenFrame(dualScreen = frameMode == ShellFrameMode.DUAL)
                    }
                    drawContent()
                },
    )
}

/**
 * Paints the screen frame around the viewport content: a near-black bezel
 * ring with a soft drop shadow and a faint top sheen, the openings stroked
 * with an inner shadow so the screens read as recessed, and (dual-screen
 * only) the body-colored hinge band with cylindrical shading across the
 * seam. Local coordinates: the content rect is (0, 0, width, height); the
 * frame overhangs it by 12dp on every side.
 */
private fun DrawScope.drawHandheldScreenFrame(dualScreen: Boolean) {
    val overhang = 12.dp.toPx()
    val outerRadius = 20.dp.toPx()
    val holeRadius = 10.dp.toPx()
    val bandHeightPx = GameScreenLayout.HINGE_BAND_HEIGHT.toPx()

    val outer =
        Rect(
            left = -overhang,
            top = -overhang,
            right = size.width + overhang,
            bottom = size.height + overhang,
        )

    val midY = size.height / 2f
    val halfBand = bandHeightPx / 2f

    val openings =
        if (dualScreen) {
            listOf(
                Rect(0f, 0f, size.width, midY - halfBand),
                Rect(0f, midY + halfBand, size.width, size.height),
            )
        } else {
            listOf(Rect(0f, 0f, size.width, size.height))
        }
    val bandRect =
        if (dualScreen) {
            Rect(outer.left, midY - halfBand, outer.right, midY + halfBand)
        } else {
            null
        }

    fun framePath(outerDy: Float): Path =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(
                RoundRect(
                    Rect(outer.left, outer.top + outerDy, outer.right, outer.bottom + outerDy),
                    CornerRadius(outerRadius),
                ),
            )
            openings.forEach { addRoundRect(RoundRect(it, CornerRadius(holeRadius))) }
            if (bandRect != null) addRect(bandRect)
        }

    // Drop shadow of the whole frame onto the body below.
    drawPath(
        path = framePath(outerDy = 5.dp.toPx()),
        color = Color.Black.copy(alpha = 0.35f),
    )

    // Hinge band: body-colored base with a cylindrical shade, so the strip
    // between the screens reads as the console's hinge barrel. The bezel
    // fill below leaves this strip unpainted; the band's own details (L/R
    // marks, dots) are drawn by a separate composable on top.
    if (bandRect != null) {
        drawRect(
            brush =
                Brush.verticalGradient(
                    listOf(ShellTheme.bodyTop, ShellTheme.bodyBottom),
                    startY = bandRect.top,
                    endY = bandRect.bottom,
                ),
            topLeft = bandRect.topLeft,
            size = bandRect.size,
        )
        drawRect(
            brush =
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.10f),
                        Color.Black.copy(alpha = 0.36f),
                    ),
                    startY = bandRect.top,
                    endY = bandRect.bottom,
                ),
            topLeft = bandRect.topLeft,
            size = bandRect.size,
        )
    }

    // Bezel fill (frame ring only), plus a faint diagonal sheen on the ring.
    drawPath(
        path = framePath(outerDy = 0f),
        brush =
            Brush.verticalGradient(
                listOf(ShellTheme.bezelTop, ShellTheme.bezelBottom),
                startY = outer.top,
                endY = outer.bottom,
            ),
    )
    drawPath(
        path = framePath(outerDy = 0f),
        brush =
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0f)),
                start = Offset(outer.left, outer.top),
                end = Offset(outer.left + outer.width * 0.55f, outer.top + outer.height * 0.55f),
            ),
    )

    // Inner shadow stroke inside each opening and a hairline highlight just
    // under its top edge: the screens sit *in* the frame, not on it.
    val strokeWidth = 2.dp.toPx()
    openings.forEach { opening ->
        val inset = strokeWidth / 2f
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(opening.left + inset, opening.top + inset),
            size = Size(opening.width - strokeWidth, opening.height - strokeWidth),
            cornerRadius = CornerRadius(holeRadius),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.10f),
            start = Offset(opening.left + holeRadius, opening.top + 1.dp.toPx()),
            end = Offset(opening.right - holeRadius, opening.top + 1.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )
    }

    // Outer rim: dark outline with a hairline highlight along the top.
    drawRoundRect(
        color = ShellTheme.bezelBorder,
        topLeft = outer.topLeft,
        size = outer.size,
        cornerRadius = CornerRadius(outerRadius),
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawLine(
        color = Color.White.copy(alpha = 0.12f),
        start = Offset(outer.left + outerRadius, outer.top + 1.dp.toPx()),
        end = Offset(outer.right - outerRadius, outer.top + 1.dp.toPx()),
        strokeWidth = 1.dp.toPx(),
    )
}
