package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapShader
import android.graphics.Shader
import kotlin.random.Random
import com.swordfish.lemuroid.R

/**
 * Central palette of the portrait handheld shell: dark charcoal body in the
 * style of a real handheld console, near-black screen frames and subtle
 * light-gray details. Everything shell-related (body, bezel, hinge band,
 * speaker grille, fast-forward button) reads its colors from here.
 */
object ShellTheme {
    val bodyTop = Color(0xFF333338)
    val bodyBottom = Color(0xFF26262B)
    val bezelTop = Color(0xFF121214)
    val bezelBottom = Color(0xFF0A0A0C)
    val bezelBorder = Color.Black.copy(alpha = 0.6f)
    val detailLabel = Color(0xFFC9C9D1)
    val grilleDot = Color(0xFF131315)
    val powerLed = Color(0xFF7ED491)
    val fastForwardInactive = Color(0xFF3A3A41)
    val fastForwardActive = Color(0xFFB7A6E8)
    val fastForwardLabel = Color(0xFFC9C9D1)
}

/**
 * Per-game shell color themes (smart adaptation, v1): the shell's colors
 * follow the game being played — the theme only swaps colors, never the
 * shell's structure, layout, button positions or touch areas. The palettes
 * are renderings of Nintendo's real official hardware colors (Nintendo
 * publishes color *names*, not hex values, so the hexes below are this
 * project's rendering of those named colors):
 * - 经典银灰: DS Lite Gloss Silver — the reference shell's light-gray
 *   textured body. This is the default: the reference images the user
 *   sent (two Douyin screenshots of a DS emulator shell) show a silver-
 *   gray body, and the old flat dark charcoal is explicitly not the
 *   target ("不是现在平的深灰").
 * - 珍珠白: DS Lite Polar White (glossy white).
 * - 午夜黑: DSi Matte Black (flat near-black).
 * - 海蓝: DS Lite Cobalt/Black — cobalt-blue top fading to a black base,
 *   exactly the two-tone hardware (colored lid, black base).
 * - 樱桃红: DS Lite Crimson/Black — crimson top fading to a black base.
 *
 * True to the real hardware, the screen bezel stays near-black on every
 * theme (DS Lite frames are black on all colors) — only the body,
 * details, accent and keys change. The fast-forward button keeps the
 * shared ShellTheme colors on all themes (v1; a dark key reads fine on
 * every body).
 */
enum class ShellColorTheme(
    /** Stable id used by the seed database's optional per-game override. */
    val id: String,
    /** Name shown in the launch toast, e.g. "珍珠白". */
    val displayName: String,
    /** Body gradient: top of the shell. */
    val bodyTop: Color,
    /** Body gradient: bottom of the shell. */
    val bodyBottom: Color,
    /** POWER / FPS / hinge micro-labels. */
    val detailLabel: Color,
    /** Power LED dot. */
    val accent: Color,
    /** Disc socket behind the D-pad / ABXY dials (PadKit level1 fill). */
    val padDisc: Color,
    /** Face-key base fill (PadKit level3 fill, unpressed). */
    val buttonBase: Color,
    /** Key letters and small icons (PadKit icons tint, unpressed). */
    val buttonLetter: Color,
) {
    SILVER(
        id = "silver",
        displayName = "经典银灰",
        bodyTop = Color(0xFFBDBDC2),
        bodyBottom = Color(0xFF9A9AA0),
        detailLabel = Color(0xFF2A2A2E),
        accent = ShellTheme.powerLed,
        padDisc = Color(0xFF8A8A90),
        buttonBase = Color(0xFFD8D8DC),
        buttonLetter = Color(0xFF2A2A2E),
    ),
    PEARL(
        id = "pearl",
        displayName = "珍珠白",
        bodyTop = Color(0xFFF6F6F8),
        bodyBottom = Color(0xFFDBDBE1),
        detailLabel = Color(0xFF55555E),
        accent = Color(0xFF4CAF6D),
        padDisc = Color(0f, 0f, 0f, 0.12f),
        buttonBase = Color(1f, 1f, 1f, 0.85f),
        buttonLetter = Color(0f, 0f, 0f, 0.80f),
    ),
    MIDNIGHT(
        id = "midnight",
        displayName = "午夜黑",
        bodyTop = Color(0xFF232329),
        bodyBottom = Color(0xFF121215),
        detailLabel = ShellTheme.detailLabel,
        accent = ShellTheme.powerLed,
        padDisc = Color(0f, 0f, 0f, 0.45f),
        buttonBase = Color(0xFF2E3038),
        buttonLetter = Color(1f, 1f, 1f, 0.85f),
    ),
    OCEAN(
        id = "ocean",
        displayName = "海蓝",
        bodyTop = Color(0xFF2A4A9E),
        bodyBottom = Color(0xFF14161D),
        detailLabel = Color(0xFFD5DCE8),
        accent = ShellTheme.powerLed,
        padDisc = Color(0f, 0f, 0f, 0.35f),
        buttonBase = Color(0xFFE9EEF8).copy(alpha = 0.85f),
        buttonLetter = Color(0xFF1F2A44).copy(alpha = 0.80f),
    ),
    CHERRY(
        id = "cherry",
        displayName = "樱桃红",
        bodyTop = Color(0xFFB3273B),
        bodyBottom = Color(0xFF1B1214),
        detailLabel = Color(0xFFE8D5D8),
        accent = ShellTheme.powerLed,
        padDisc = Color(0f, 0f, 0f, 0.35f),
        buttonBase = Color(0xFFF9E9EB).copy(alpha = 0.85f),
        buttonLetter = Color(0xFF4A1F26).copy(alpha = 0.80f),
    ),
    ;

    companion object {
        /** Unknown or missing ids fall back to the classic silver shell. */
        fun fromId(id: String?): ShellColorTheme {
            if (id.isNullOrBlank()) return SILVER
            return entries.firstOrNull { it.id == id.trim().lowercase() } ?: SILVER
        }

        /**
         * Face-button letter colors (SNES / New 3DS style): X blue, Y
         * green, A red, B yellow. The same on every shell theme — the
         * letters are the buttons' identity, not the shell's color — and
         * readable on both light and dark key bases. Used by the melonDS
         * shell's PadKit theme (see LemuroidPadTheme.faceButtonLetterColors).
         */
        val ABXY_LETTER_COLORS: Map<String, Color> =
            mapOf(
                "X" to Color(0xFF2F7DE1),
                "Y" to Color(0xFF23A55B),
                "A" to Color(0xFFE5484D),
                "B" to Color(0xFFF2C200),
            )
    }
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
 *
 * DS Lite gloss: the plate's gradient is the per-game shell theme's body
 * colors, plus a soft white highlight band under the top edge and a
 * brighter top hairline, so the plastic reads as glossy rather than flat
 * paint. The highlight is white-alpha overlay, so it works on every theme
 * (white shells get a gentle sheen, dark shells a specular edge).
 *
 * [showPowerRow] hides the POWER row (LED + label, and the compact
 * fast-forward button when set) for the melonDS path, whose status strip
 * above the deck already carries »» + POWER + FPS — leaving the row on
 * would duplicate POWER. The libretro path leaves it on (default) and
 * renders exactly as before.
 */
@Composable
fun HandheldShellBody(
    modifier: Modifier = Modifier,
    style: ShellStyle = ShellStyle.CHARCOAL,
    bodyTop: Color = style.top,
    bodyBottom: Color = style.bottom,
    showPowerRow: Boolean = true,
    // Optional fast-forward control (melonDS engine path only): a compact
    // »» round button at the POWER row's left end, clear of every pad —
    // the row is the deck's function/status strip, so a function button
    // belongs here. The libretro path leaves this null and the row
    // renders exactly as before (POWER LED + label, centered).
    fastForwardEnabled: Boolean = false,
    onToggleFastForward: (() -> Unit)? = null,
    // Fine matte grain (DS Lite textured plastic). Opt-in: the melonDS
    // path passes true; the libretro path leaves it off and renders
    // exactly as before.
    grain: Boolean = false,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val grainBrush = rememberFineGrainBrush()

    Box(
        modifier =
            modifier
                .shadow(elevation = 16.dp, shape = shape, clip = false)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(bodyTop, bodyBottom)))
                .drawBehind {
                    val inset = 28.dp.toPx()
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.30f),
                        start = Offset(inset, stroke),
                        end = Offset(size.width - inset, stroke),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    // Glossy-plastic highlight: a soft white falloff under
                    // the top edge, so the plate's top catches the light
                    // like the DS Lite's glossy shell.
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.0f),
                                ),
                                startY = 0f,
                                endY = 48.dp.toPx(),
                            ),
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
                    // Fine matte grain (DS Lite textured plastic).
                    if (grain) {
                        drawRect(brush = grainBrush)
                    }
                },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Power LED with its micro label, like on real handhelds: a small
        // lived detail on the deck, centered clear of the two pads. The
        // fast-forward button (engine path only) rides at the row's left
        // end; the FPS readout used to ride at the right end, but as a
        // long text strip it crowded the R shoulder and read as clutter —
        // it now lives as a small status line at the deck's bottom center
        // (see MelonDSGameShell).
        if (showPowerRow) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp, start = 16.dp, end = 16.dp),
            ) {
                if (onToggleFastForward != null) {
                    HandheldFastForwardButton(
                        modifier = Modifier.align(Alignment.TopStart),
                        fastForwardSpeed = if (fastForwardEnabled) 2 else 1,
                        preferredSpeed = 2,
                        onToggle = onToggleFastForward,
                        onSelectSpeed = {},
                        showSpeedMenu = false,
                        compact = true,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.TopCenter),
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
/**
 * Fine matte grain for the DS Lite body (reference shell: light-gray
 * plastic with a fine matte / carbon-fiber feel, not flat paint). A
 * small noise bitmap tiled as a shader brush — one draw call, no
 * per-dot cost. The speckle is black + white dots at very low alpha,
 * so it reads as texture on every shell theme (light or dark) without
 * tinting it. Opt-in per shell (the melonDS path); the libretro path
 * keeps its flat body.
 */
private class TiledGrainBrush(private val bitmap: ImageBitmap) : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        BitmapShader(bitmap.asAndroidBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

private fun createGrainImageBitmap(sizePx: Int = 48): ImageBitmap {
    val bitmap = ImageBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val random = Random(0x5EED)
    repeat(140) {
        val x = random.nextFloat() * sizePx
        val y = random.nextFloat() * sizePx
        val light = random.nextBoolean()
        val paint =
            Paint().apply {
                color =
                    if (light) {
                        Color.White.copy(alpha = 14 / 255f)
                    } else {
                        Color.Black.copy(alpha = 18 / 255f)
                    }
            }
        canvas.drawCircle(Offset(x, y), 0.8f, paint)
    }
    return bitmap
}

@Composable
private fun rememberFineGrainBrush(): Brush = remember { TiledGrainBrush(createGrainImageBitmap()) }

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
    grainBrush: Brush? = null,
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
    // DS Lite gloss: a faint diagonal sheen over the whole body, so the
    // plastic catches light instead of reading as flat paint. White-alpha
    // only, so it tints every shell theme the same way and never fights
    // the theme's own colors.
    drawPath(
        path = path,
        brush =
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0f)),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.55f, size.height * 0.55f),
            ),
    )
    // Fine matte grain (DS Lite textured plastic), over the same path so
    // the texture stays inside the body shape and out of the windows.
    if (grainBrush != null) {
        drawPath(path = path, brush = grainBrush)
    }
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
    bodyTop: Color = style.top,
    bodyBottom: Color = style.bottom,
    screenRect: Rect? = null,
    dualScreen: Boolean = false,
    holeInflate: Dp = 0.dp,
    // Fine matte grain (DS Lite textured plastic). Opt-in: the melonDS
    // path passes true; the libretro path leaves it off and renders
    // exactly as before.
    grain: Boolean = false,
) {
    var ownBounds by remember { mutableStateOf<Rect?>(null) }
    val grainBrush = rememberFineGrainBrush()

    Box(
        modifier =
            modifier
                .onGloballyPositioned { ownBounds = it.boundsInRoot() }
                .drawWithContent {
                    drawShellFill(
                        brush = Brush.verticalGradient(listOf(bodyTop, bodyBottom)),
                        outerCornerRadiusPx = 0f,
                        screenRect = screenRect,
                        ownBounds = ownBounds,
                        holeCornerRadiusPx = 10.dp.toPx(),
                        dualScreen = dualScreen,
                        bandHeightPx = GameScreenLayout.HINGE_BAND_HEIGHT.toPx(),
                        holeInflatePx = holeInflate.toPx(),
                        grainBrush = if (grain) grainBrush else null,
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
 * itself is a capsule with the shell theme's body colors and a cylindrical
 * shade (dark edges, bright middle), like the DS hinge barrel — it is
 * painted here, on top of the game surface, because the frame layer's own
 * band sits *below* the surface on the melonDS path and would be hidden
 * by it. It is laid out centered on the seam between the screens, with
 * the same height the shell fill leaves for the band, so the details stay
 * inside the strip.
 */
@Composable
fun HandheldHingeBand(
    modifier: Modifier = Modifier,
    shellTheme: ShellColorTheme = ShellColorTheme.SILVER,
) {
    Row(
        modifier =
            modifier
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(shellTheme.bodyTop, shellTheme.bodyBottom),
                        ),
                    shape = RoundedCornerShape(50),
                )
                .drawBehind {
                    // Cylindrical shade over the body gradient: dark rims,
                    // bright belly — the barrel reads as round, not flat.
                    drawRoundRect(
                        brush =
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.40f),
                                ),
                            ),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "L",
            color = shellTheme.detailLabel,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
        )
        SpeakerDots(count = 5)
        Text(
            text = "R",
            color = shellTheme.detailLabel,
            fontSize = 7.5.sp,
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

    /** Two screen windows divided by the real hinge gap (DS shell). */
    DUAL,
}

/**
 * The game viewport placeholder, with the handheld screen frame drawn in
 * its own draw pass for the geometry-driven shells.
 *
 * The placeholder is what the layout measures to position the GL viewport,
 * and this composable keeps the modifier chain that measurement has always
 * used (layout id, position callback), then adds the frame drawing on the
 * *same* layout node. The frame is therefore painted in the very coordinate
 * system the viewport is reported in: its openings cannot drift from the
 * rendered game. The frame overhangs the placeholder on every side (drawing
 * is not clipped to the node bounds), forming the bezel around the content.
 * Generic shells also pad the placeholder by the display-cutout inset; the
 * geometry-driven shells must not (see [GameViewportShellBox]).
 *
 * In dual-screen mode the content rect holds two genuinely separate 4:3
 * windows divided by the real hinge gap ([hingeGap]); the frame draws
 * one bezel ring per window and leaves the gap unframed. The hinge
 * barrel (L/R marks, speaker dots) is a separate composable laid out
 * over the gap.
 */
@Composable
fun GameViewportShellBox(
    frameMode: ShellFrameMode,
    onPositioned: (Rect) -> Unit,
    applyCutoutPadding: Boolean = true,
    // Real hinge gap between the two windows in dual-screen mode (melonDS
    // path only; 0 for single-screen). The bezel stays the shared
    // near-black on every shell theme, so the frame takes no theme.
    hingeGap: Dp = 0.dp,
    // Optional content hosted inside the viewport (M4): the melonDS shell
    // embeds its GLSurfaceView here via AndroidView, so the surface is
    // sized exactly to the frame rect and the frame drawn by this box lines
    // up with it by construction. The libretro path leaves this empty and
    // keeps its full-screen surface below the shell layers.
    content: @Composable () -> Unit = {},
) {
    // The geometry-driven shells (frameMode != NONE) size this placeholder
    // to exactly the core frame's aspect and fold the display cutout into
    // the layout's top margin instead: padding here would grow the reported
    // bounds beyond the sized rect (v3.2 double-counted the cutout that
    // way, and the renderer letterboxed the frame inside the too-tall
    // viewport). Generic shells keep the historical cutout padding.
    val cutoutPadding =
        if (applyCutoutPadding) {
            Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
        } else {
            Modifier
        }
    Box(
        modifier =
            Modifier
                .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                .then(cutoutPadding)
                .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
                .drawWithContent {
                    if (frameMode != ShellFrameMode.NONE) {
                        drawHandheldScreenFrame(
                            dualScreen = frameMode == ShellFrameMode.DUAL,
                            hingeGap = hingeGap,
                        )
                    }
                    drawContent()
                },
    ) {
        content()
    }
}

/**
 * Paints the screen frame(s) around the viewport content: near-black
 * bezel rings with a soft drop shadow and a faint top sheen, the
 * openings stroked with an inner shadow so the screens read as recessed.
 * Local coordinates: the content rect is (0, 0, width, height); each
 * frame overhangs its window by 16dp on every side.
 *
 * Dual-screen (melonDS path): TWO separate frames, one per 4:3 window,
 * divided by the real hinge gap ([hingeGap]) — the gap itself is not
 * framed at all (the renderer leaves it undrawn and the shell's opaque
 * hinge barrel covers the middle of it), so the two screens read as
 * two separate screens, not one long screen with a line across it.
 * The bezel stays the shared near-black on every shell theme, true to
 * the DS Lite (black screen frames on all colors).
 *
 * Single-screen (libretro path): one frame around the whole viewport,
 * exactly as before.
 *
 * The reference shell's speaker is a dot matrix + volume slider on the
 * left of the bottom screen (a shell-layer composable), not slits
 * flanking the top screen — so this frame draws no slits.
 */
private fun DrawScope.drawHandheldScreenFrame(
    dualScreen: Boolean,
    hingeGap: Dp = 0.dp,
) {
    val overhang = 16.dp.toPx()
    val outerRadius = 24.dp.toPx()
    val holeRadius = 10.dp.toPx()

    if (dualScreen) {
        val gapPx = hingeGap.toPx()
        val windowHeight = (size.height - gapPx) / 2f
        drawSingleScreenFrame(
            outer =
                Rect(
                    left = -overhang,
                    top = -overhang,
                    right = size.width + overhang,
                    bottom = windowHeight + overhang,
                ),
            opening = Rect(0f, 0f, size.width, windowHeight),
            outerRadius = outerRadius,
            holeRadius = holeRadius,
        )
        drawSingleScreenFrame(
            outer =
                Rect(
                    left = -overhang,
                    top = windowHeight + gapPx - overhang,
                    right = size.width + overhang,
                    bottom = size.height + overhang,
                ),
            opening = Rect(0f, windowHeight + gapPx, size.width, size.height),
            outerRadius = outerRadius,
            holeRadius = holeRadius,
        )
        return
    }
    drawSingleScreenFrame(
        outer =
            Rect(
                left = -overhang,
                top = -overhang,
                right = size.width + overhang,
                bottom = size.height + overhang,
            ),
        opening = Rect(0f, 0f, size.width, size.height),
        outerRadius = outerRadius,
        holeRadius = holeRadius,
    )
}

/** One bezel ring: drop shadow + near-black fill + sheen + inner shadow + rim. */
private fun DrawScope.drawSingleScreenFrame(
    outer: Rect,
    opening: Rect,
    outerRadius: Float,
    holeRadius: Float,
) {
    fun framePath(outerDy: Float): Path =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(
                RoundRect(
                    Rect(outer.left, outer.top + outerDy, outer.right, outer.bottom + outerDy),
                    CornerRadius(outerRadius),
                ),
            )
            addRoundRect(RoundRect(opening, CornerRadius(holeRadius)))
        }

    // Drop shadow of the frame onto the body below.
    drawPath(
        path = framePath(outerDy = 5.dp.toPx()),
        color = Color.Black.copy(alpha = 0.35f),
    )

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

    // Inner shadow stroke inside the opening and a hairline highlight just
    // under its top edge: the screen sits *in* the frame, not on it.
    val strokeWidth = 2.dp.toPx()
    val inset = strokeWidth / 2f
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.65f),
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

/**
 * On-screen fast-forward control for the handheld shell, sitting at the
 * bottom center of the deck between the pads. A tap toggles fast-forward at
 * the selected multiplier; a long press opens the multiplier menu — unless
 * [showSpeedMenu] is false (the melonDS engine path: its fast-forward is a
 * Boolean toggle with no speed gears, so the menu would offer speeds that do
 * nothing). Shared by the libretro shell (MobileGameScreen) and the melonDS
 * shell (MelonDSGameShell) so both decks show the same pill.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HandheldFastForwardButton(
    modifier: Modifier = Modifier,
    fastForwardSpeed: Int,
    preferredSpeed: Int,
    onToggle: () -> Unit,
    onSelectSpeed: (Int) -> Unit,
    speedOptions: List<Int> = listOf(2, 3, 4),
    showSpeedMenu: Boolean = true,
    /**
     * Compact form for the POWER row (melonDS engine path): a 32dp round
     * button with no "2x" label underneath — the row is only ~40dp tall
     * and the speed is fixed at 2x on this engine (toggle-only, no
     * gears), so the label would buy nothing. The full pill (44dp +
     * label) stays the default for the libretro deck-bottom placement.
     */
    compact: Boolean = false,
) {
    val isActive = fastForwardSpeed > 1
    val displayedSpeed = if (isActive) fastForwardSpeed else preferredSpeed
    var menuExpanded by remember { mutableStateOf(false) }
    val buttonDescription =
        stringResource(R.string.game_fast_forward_button)

    Box(modifier = modifier.wrapContentSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(if (compact) 32.dp else 44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) {
                                ShellTheme.fastForwardActive
                            } else {
                                ShellTheme.fastForwardInactive.copy(alpha = 0.9f)
                            },
                        )
                        .combinedClickable(
                            onClick = onToggle,
                            onLongClick = { if (showSpeedMenu) menuExpanded = true },
                        )
                        .semantics { contentDescription = buttonDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "»»",
                    color = if (isActive) Color(0xFF2E2150) else Color.White,
                    fontSize = if (compact) 14.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!compact) {
                Text(
                    text = "${displayedSpeed}x",
                    color = ShellTheme.fastForwardLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (showSpeedMenu) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                speedOptions.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text(text = "${speed}x") },
                        onClick = {
                            onSelectSpeed(speed)
                            menuExpanded = false
                        },
                        leadingIcon = {
                            if (speed == preferredSpeed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
