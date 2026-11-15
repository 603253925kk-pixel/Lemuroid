package com.swordfish.lemuroid.app.shared.game.melonds

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import com.swordfish.lemuroid.app.mobile.feature.game.GameScreenLayout
import com.swordfish.lemuroid.app.mobile.feature.game.GameViewportShellBox
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldFastForwardButton
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldHingeBand
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldShellBody
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldShellFullBody
import com.swordfish.lemuroid.app.mobile.feature.game.ShellColorTheme
import com.swordfish.lemuroid.app.mobile.feature.game.ShellFrameMode
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputevents.InputEvent

/**
 * Portrait handheld-shell layout for the vendored melonDS engine (M4, deck
 * redone in #54).
 *
 * This mirrors the v3.3 libretro shell (MobileGameScreen + HandheldShell.kt)
 * with one structural difference: the libretro path keeps a full-screen GL
 * surface *below* the shell layers and cuts holes in them, while here the
 * existing [GLSurfaceView] is hosted *inside* [GameViewportShellBox] via
 * [AndroidView], sized exactly to the geometry-driven dual-window viewport
 * (two 4:3 windows + the hinge gap). The frame the box draws therefore
 * lines up with the game by construction, and the surface never needs a
 * viewport rect of its own — the renderer already aspect-fits each 256x192
 * core half into its own window.
 *
 * The controls deck is the v3.3 deck itself, not a re-skin of the M2 flat
 * buttons: the same PadKit MELONDS touch-controller layouts
 * ([TouchControllerID.MELONDS]) the libretro path uses — dark recessed
 * discs, light D-pad cross, light-gray round ABXY, L/R shoulders, mic and
 * lid-close secondaries — hosted in a [PadKit] whose input events go to the
 * activity's [onPadInputEvent] (mapped onto [me.magnum.melonds.MelonEmulator]
 * there). The pads read the same saved touch settings as the libretro path
 * (scale/margins under the MELONDS portrait key), so a user's pad sizing
 * carries over.
 *
 * Status strip: the fast-forward control and the FPS readout live in their
 * own dedicated 40dp strip below the viewport — one centered row: compact
 * »» button, POWER LED + label, FPS text. The strip owns that whole band
 * (the deck reserve grew 300dp to 340dp to make room), so neither element
 * can land on a key: slice 7 put the »» at the POWER row's left end (on
 * top of the L shoulder) and the FPS line at the deck's bottom center (on
 * top of the lid-close ×), and the #59 device test caught both overlaps.
 * The pads themselves are left exactly as the v3.3 layout places them
 * (bottom-anchored, tops free): PadKit's radial layout centers each dial
 * in its box, so linking a pad's top to the strip would lift the whole
 * deck off the bottom edge. The strip starts 12dp below the viewport so
 * the bottom Nintendo logo band stays clear of it.
 *
 * Two separate screens: the viewport is two genuinely separate 4:3
 * windows (top 256x192, bottom 256x192) divided by a real 28dp hinge
 * gap — the renderer maps each texture half onto its own window and
 * leaves the gap undrawn, so each screen shows every pixel and the two
 * screens read as two screens, not one long screen with a line across
 * it. The hinge barrel (a 12dp capsule with cylindrical shading, L/R
 * marks and speaker dots) sits centered in the gap. The old overlay
 * band (which covered ~3 game pixels per side of the seam) is gone.
 *
 * Shell theme: [shellTheme] recolors the shell per game (smart adaptation
 * resolves it at launch from the seed override, then the game's title
 * keywords, else classic silver). Only colors change — the theme drives
 * the body gradient, the hinge barrel, the POWER/FPS labels and the PadKit
 * key colors; the bezel stays near-black on every theme, true to the DS
 * Lite (black screen frames on all colors). The activity is portrait-locked
 * (see the manifest), so this layout never has to survive a rotation-driven
 * surface re-creation.
 *
 * Geometry comes from [GameScreenLayout.computePortraitDualScreenGeometry]
 * (two 4:3 windows + the hinge gap), not the libretro path's single 2:3
 * block, so the two paths render their own shells on the same phone.
 */
@Composable
fun MelonDSGameShellContent(
    surfaceView: GLSurfaceView,
    fpsText: String,
    fastForwardEnabled: Boolean,
    onToggleFastForward: () -> Unit,
    onPadInputEvent: (List<InputEvent>) -> Unit,
    shellTheme: ShellColorTheme = ShellColorTheme.SILVER,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Same saved touch settings the libretro path loads for the MELONDS
    // controller (scale/margins under touch_controller_settings_MELONDS_PORTRAIT).
    val touchSettingsManager =
        remember(context) {
            TouchControllerSettingsManager(SharedPreferencesHelper.getSharedPreferences(context))
        }
    val touchSettings by touchSettingsManager
        .observeSettings(
            TouchControllerID.MELONDS,
            TouchControllerSettingsManager.Orientation.PORTRAIT,
            density,
            WindowInsets.displayCutout,
        )
        .collectAsState(initial = TouchControllerSettingsManager.Settings())
    val padConfig = TouchControllerID.getConfig(TouchControllerID.MELONDS)

    // PadKit observes touches in its bounds and only reports state changes
    // for its registered controls; it does not consume the surface's touches
    // (the libretro path hosts its full-screen GL view inside PadKit the
    // same way). No haptics here: the activity has no haptic setting wired.
    PadKit(
        modifier = Modifier.fillMaxSize(),
        onInputEvents = onPadInputEvent,
        hapticFeedbackType = HapticFeedbackType.NONE,
    ) {
        // Captured explicitly: inside the nested ConstraintLayout lambda a
        // bare `this` would resolve to the layout scope, not the PadKit one.
        val padScope = this

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) {
            val cutoutTop =
                with(density) {
                    WindowInsets.displayCutout.getTop(density).toDp()
                }
            // Two genuinely separate 4:3 windows + the real hinge gap
            // (see computePortraitDualScreenGeometry): the libretro
            // path's single 2:3 block is not used here.
            val screenGeometry =
                GameScreenLayout.computePortraitDualScreenGeometry(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    cutoutTop = cutoutTop,
                    deckReserve = MELONDS_DECK_RESERVE,
                )

            if (screenGeometry == null) {
                // Absurdly small display: fall back to a bare surface + FPS
                // line instead of crashing on a null geometry. Never
                // expected on a real phone.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AndroidView(
                        factory = { surfaceView },
                        modifier = Modifier.fillMaxSize().weight(1f),
                    )
                    androidx.compose.material3.Text(
                        text = fpsText,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                return@BoxWithConstraints
            }

            var viewportPosition by remember { mutableStateOf<Rect?>(null) }

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet = melonDSShellConstraintSet(screenGeometry),
            ) {
                // One integrated body behind everything: it stays transparent
                // at the screen windows (hole inflated 8dp — the frame drawn by
                // the viewport box itself defines the exact window edges) and
                // paints the hinge strip across the seam.
                HandheldShellFullBody(
                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_SHELL_BODY),
                    bodyTop = shellTheme.bodyTop,
                    bodyBottom = shellTheme.bodyBottom,
                    screenRect = viewportPosition,
                    dualScreen = false,
                    holeInflate = 8.dp,
                    grain = true,
                )

                GameViewportShellBox(
                    frameMode = ShellFrameMode.DUAL,
                    hingeGap = screenGeometry.hingeGap,
                    onPositioned = { viewportPosition = it },
                    // The cutout is already folded into the top margin by the
                    // geometry; padding here as well would double-count it and
                    // letterbox the game inside its own window (the v3.2 bug).
                    applyCutoutPadding = false,
                ) {
                    AndroidView(
                        factory = { surfaceView },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Hinge barrel centered in the real gap between the two
                // windows, drawn after the frame so it sits on top of the
                // gap (the gap itself is never mapped to game content).
                HandheldHingeBand(
                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_HINGE_BAND),
                    shellTheme = shellTheme,
                )

                // Nintendo logo strip above the top window: a raised
                // body-colored band with the oval logo and the power
                // (blue) + charging (yellow) LEDs.
                Box(
                    modifier =
                        Modifier
                            .layoutId(MELONDS_LOGO_STRIP_TOP)
                            .background(shellTheme.bodyTop, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ShellLogoStrip(
                        modifier = Modifier.fillMaxSize(),
                        shellTheme = shellTheme,
                        onDark = false,
                    )
                }

                // Nintendo logo strip on the bottom window's lower bezel
                // band (medium-gray logo on black, LEDs stay bright).
                ShellLogoStrip(
                    modifier = Modifier.layoutId(MELONDS_LOGO_STRIP_BOTTOM),
                    shellTheme = shellTheme,
                    onDark = true,
                )

                // Speaker dot matrix + volume slider on the left bezel at
                // the bottom window's height. Pure decoration — no touch
                // handling, so it can never swallow a tap.
                ShellSpeakerColumn(
                    modifier = Modifier.layoutId(MELONDS_SPEAKER_COLUMN),
                )

                // Lower body: the shell plate, drawn first so the pads sit
                // on it. The melonDS path hides the plate's own POWER row —
                // »» + POWER + FPS live in the status strip above the deck
                // instead — and the plate runs up to the strip's top, so the
                // strip reads as the top band of the plate.
                Box(
                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                ) {
                    HandheldShellBody(
                        modifier = Modifier.fillMaxSize(),
                        bodyTop = shellTheme.bodyTop,
                        bodyBottom = shellTheme.bodyBottom,
                        showPowerRow = false,
                        grain = true,
                    )
                }

                // The v3.3 pads, direct ConstraintLayout children like on the
                // libretro path, in the per-game shell theme's key colors
                // (disc base, button base, letter color).
                CompositionLocalProvider(
                    LocalLemuroidPadTheme provides
                        LemuroidPadTheme(
                            level1FillOverride = shellTheme.padDisc,
                            level3FillOverride = shellTheme.buttonBase,
                            iconsOverride = shellTheme.buttonLetter,
                            // SNES / New 3DS colored letters: X blue,
                            // Y green, A red, B yellow.
                            faceButtonLetterColors = ShellColorTheme.ABXY_LETTER_COLORS,
                        ),
                ) {
                    padConfig.leftComposable(
                        padScope,
                        Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                        touchSettings,
                    )
                    padConfig.rightComposable(
                        padScope,
                        Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                        touchSettings,
                    )
                }

                // Status strip: »» + POWER + FPS in one centered row,
                // owning a 40dp band between the viewport bottom and the
                // pads' tops. It cannot collide with a key — nothing else
                // is allowed in this band. (Slice 7 put the »» at the
                // POWER row's left end, on top of the L shoulder, and the
                // FPS line at the deck's bottom center, on top of the
                // lid-close ×; the #59 device test caught both overlaps.)
                Row(
                    modifier = Modifier.layoutId(MELONDS_STATUS_STRIP),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HandheldFastForwardButton(
                        fastForwardSpeed = if (fastForwardEnabled) 2 else 1,
                        preferredSpeed = 2,
                        onToggle = onToggleFastForward,
                        onSelectSpeed = {},
                        showSpeedMenu = false,
                        compact = true,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(shellTheme.accent),
                    )
                    androidx.compose.material3.Text(
                        text = "POWER",
                        color = shellTheme.detailLabel.copy(alpha = 0.75f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    androidx.compose.material3.Text(
                        text = fpsText,
                        color = shellTheme.detailLabel.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Portrait constraint set for the melonDS shell: the viewport gets its
 * exact two-window geometry size (two 4:3 windows + the real hinge gap),
 * the hinge barrel sits centered in the gap, the Nintendo logo strips sit
 * above the top window's frame and on the bottom window's frame, the
 * speaker column sits on the left bezel at the bottom window's height,
 * the status strip sits 12dp below the viewport (leaving the bottom logo
 * band clear), the lower body takes everything below the strip, and the
 * pads take the deck's bottom in a spread chain — the same pad constraints
 * the libretro portrait set uses, so the v3.3 deck lands in the same place
 * on the same phone.
 */
private fun melonDSShellConstraintSet(
    screenGeometry: GameScreenLayout.DualScreenGeometry,
): ConstraintSet =
    ConstraintSet {
        val shellBody = createRefFor(GameScreenLayout.CONSTRAINTS_SHELL_BODY)
        val gameView = createRefFor(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
        val hingeBand = createRefFor(GameScreenLayout.CONSTRAINTS_HINGE_BAND)
        val bottomContainer = createRefFor(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER)
        val leftPad = createRefFor(GameScreenLayout.CONSTRAINTS_LEFT_PAD)
        val rightPad = createRefFor(GameScreenLayout.CONSTRAINTS_RIGHT_PAD)
        val statusStrip = createRefFor(MELONDS_STATUS_STRIP)
        val logoStripTop = createRefFor(MELONDS_LOGO_STRIP_TOP)
        val logoStripBottom = createRefFor(MELONDS_LOGO_STRIP_BOTTOM)
        val speakerColumn = createRefFor(MELONDS_SPEAKER_COLUMN)

        val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)

        constrain(shellBody) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom)
            absoluteLeft.linkTo(parent.absoluteLeft)
            absoluteRight.linkTo(parent.absoluteRight)
        }

        constrain(gameView) {
            width = Dimension.value(screenGeometry.contentWidth)
            height = Dimension.value(screenGeometry.contentHeight)
            top.linkTo(
                parent.top,
                margin = screenGeometry.topMargin + screenGeometry.cutoutTop,
            )
            absoluteLeft.linkTo(parent.absoluteLeft)
            absoluteRight.linkTo(parent.absoluteRight)
        }

        // Hinge barrel: a 12dp capsule centered in the 28dp real gap
        // between the two windows. It covers the middle of the gap,
        // leaving 8dp of black (the screens' own bezels) above and below
        // it. Nothing is drawn over game content — the gap was never
        // mapped to texture, and the renderer leaves it undrawn.
        constrain(hingeBand) {
            width = Dimension.value(screenGeometry.contentWidth)
            height = Dimension.value(MELONDS_HINGE_BARREL_HEIGHT)
            absoluteLeft.linkTo(gameView.absoluteLeft)
            absoluteRight.linkTo(gameView.absoluteRight)
            top.linkTo(
                gameView.top,
                margin =
                    screenGeometry.windowHeight +
                        (screenGeometry.hingeGap - MELONDS_HINGE_BARREL_HEIGHT) / 2,
            )
        }

        // Nintendo logo strip above the top window: a raised body band
        // sitting above the top frame's 16dp overhang, so it never covers
        // the bezel.
        constrain(logoStripTop) {
            width = Dimension.value(screenGeometry.contentWidth)
            height = Dimension.value(GameScreenLayout.DUAL_SCREEN_LOGO_STRIP_HEIGHT)
            absoluteLeft.linkTo(gameView.absoluteLeft)
            absoluteRight.linkTo(gameView.absoluteRight)
            bottom.linkTo(gameView.top, margin = MELONDS_FRAME_OVERHANG)
        }

        // Nintendo logo strip on the bottom frame's lower band: it sits
        // on the black bezel just below the bottom window, above the
        // status strip (which starts 12dp below the viewport).
        constrain(logoStripBottom) {
            width = Dimension.value(screenGeometry.contentWidth)
            height = Dimension.value(MELONDS_LOGO_STRIP_BOTTOM_HEIGHT)
            absoluteLeft.linkTo(gameView.absoluteLeft)
            absoluteRight.linkTo(gameView.absoluteRight)
            top.linkTo(gameView.bottom)
        }

        // Speaker column on the left bezel, centered on the bottom
        // window's height: dot matrix + VOL slider, pure decoration.
        // Exact top margin (no bias math): the bottom window's center
        // minus half the column's height.
        constrain(speakerColumn) {
            width = Dimension.value(MELONDS_SPEAKER_COLUMN_WIDTH)
            height = Dimension.value(MELONDS_SPEAKER_COLUMN_HEIGHT)
            absoluteLeft.linkTo(parent.absoluteLeft, margin = 2.dp)
            top.linkTo(
                gameView.top,
                margin =
                    screenGeometry.windowHeight + screenGeometry.hingeGap +
                        screenGeometry.windowHeight / 2 -
                        MELONDS_SPEAKER_COLUMN_HEIGHT / 2,
            )
        }

        constrain(bottomContainer) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            absoluteLeft.linkTo(parent.absoluteLeft)
            absoluteRight.linkTo(parent.absoluteRight)
            top.linkTo(statusStrip.top)
            bottom.linkTo(parent.bottom)
        }

        // Status strip: a fixed 40dp band starting 12dp below the
        // viewport, so the bottom Nintendo logo band stays clear of it.
        // The pads stay bottom-anchored with their tops free — PadKit's
        // radial layout centers each dial in its box, so linking a pad's
        // top to the strip would lift the whole deck off the bottom edge.
        // The deck reserve grew 300dp to 340dp to pay for the strip; the
        // viewport is width-driven on a tall phone, so the game does not
        // shrink.
        constrain(statusStrip) {
            width = Dimension.fillToConstraints
            height = Dimension.value(MELONDS_STATUS_STRIP_HEIGHT)
            absoluteLeft.linkTo(parent.absoluteLeft)
            absoluteRight.linkTo(parent.absoluteRight)
            top.linkTo(gameView.bottom, margin = MELONDS_LOGO_STRIP_BOTTOM_HEIGHT)
        }

        constrain(gamePadChain) {
            absoluteLeft.linkTo(parent.absoluteLeft)
            absoluteRight.linkTo(parent.absoluteRight)
        }

        constrain(rightPad) {
            width = Dimension.fillToConstraints
            bottom.linkTo(parent.bottom)
        }

        constrain(leftPad) {
            width = Dimension.fillToConstraints
            bottom.linkTo(parent.bottom)
        }
    }

/** Height the viewport sizing reserves for the controls deck below it. */
private val MELONDS_DECK_RESERVE = 340.dp

/** Height of the dedicated status strip (»» + POWER + FPS) below the viewport. */
private val MELONDS_STATUS_STRIP_HEIGHT = 40.dp

/** Layout id of the status strip row below the viewport. */
private const val MELONDS_STATUS_STRIP = "melondsStatusStrip"

/** Height of the hinge barrel capsule centered in the real hinge gap. */
private val MELONDS_HINGE_BARREL_HEIGHT = 12.dp

/** Overhang of the screen frames on every side (must match HandheldShell). */
private val MELONDS_FRAME_OVERHANG = 16.dp

/** Layout id of the Nintendo logo strip above the top window. */
private const val MELONDS_LOGO_STRIP_TOP = "melondsLogoStripTop"

/** Layout id of the Nintendo logo strip on the bottom window's bezel. */
private const val MELONDS_LOGO_STRIP_BOTTOM = "melondsLogoStripBottom"

/** Height of the bottom Nintendo logo strip (on the black bezel). */
private val MELONDS_LOGO_STRIP_BOTTOM_HEIGHT = 12.dp

/** Layout id of the speaker column on the left bezel. */
private const val MELONDS_SPEAKER_COLUMN = "melondsSpeakerColumn"

/** Size of the speaker column (dot matrix + VOL slider). */
private val MELONDS_SPEAKER_COLUMN_WIDTH = 16.dp
private val MELONDS_SPEAKER_COLUMN_HEIGHT = 64.dp

/**
 * Nintendo logo strip (reference DS shell): the oval Nintendo logo with
 * the power (blue) and charging (yellow) LEDs beside it. Pure decoration
 * — no touch handling. [onDark] puts the logo in medium gray for the
 * bottom strip, which sits directly on the black bezel; the top strip
 * sits on a body-colored band and takes the theme's label color.
 */
@Composable
private fun ShellLogoStrip(
    modifier: Modifier,
    shellTheme: ShellColorTheme,
    onDark: Boolean,
) {
    val logoColor = if (onDark) Color(0xFF9A9AA0) else shellTheme.detailLabel
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .border(1.dp, logoColor, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                text = "Nintendo",
                color = logoColor,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .size(3.dp, 7.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF6EC1F5)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier =
                Modifier
                    .size(3.dp, 7.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFFD23F)),
        )
    }
}

/**
 * Speaker dot matrix + volume slider on the left bezel at the bottom
 * window's height (reference DS shell). Pure decoration — no touch
 * handling, no pointer input, so it can never swallow a tap. The dots
 * form a small diamond cluster in medium gray; the slider slot has its
 * thumb near the top (volume up).
 */
@Composable
private fun ShellSpeakerColumn(modifier: Modifier) {
    val dotColor = Color(0xFF8A8A90)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Dot matrix: 1-2-3-1 diamond cluster.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpeakerDot(dotColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpeakerDot(dotColor)
                SpeakerDot(dotColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpeakerDot(dotColor)
                SpeakerDot(dotColor)
                SpeakerDot(dotColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SpeakerDot(dotColor)
            }
        }
        androidx.compose.material3.Text(
            text = "VOL",
            color = dotColor,
            fontSize = 5.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier =
                Modifier
                    .size(3.dp, 22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(5.dp, 3.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(dotColor),
            )
        }
        androidx.compose.material3.Text(
            text = "OFF",
            color = dotColor,
            fontSize = 5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SpeakerDot(color: Color) {
    Box(
        modifier = Modifier.size(2.5.dp).clip(CircleShape).background(color),
    )
}
