package com.swordfish.lemuroid.app.shared.game.melonds

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldHingeBand
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldShellBody
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldShellFullBody
import com.swordfish.lemuroid.app.mobile.feature.game.ShellFrameMode
import com.swordfish.lemuroid.app.mobile.feature.game.ShellTheme
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
 * [AndroidView], sized exactly to the geometry-driven 2:3 viewport. The
 * frame the box draws therefore lines up with the game by construction,
 * and the surface never needs a viewport rect of its own — the renderer
 * already aspect-fits the 256x384 core frame into whatever surface it gets.
 *
 * The controls deck is the v3.3 deck itself, not a re-skin of the M2 flat
 * buttons: the same PadKit MELONDS touch-controller layouts
 * ([TouchControllerID.MELONDS]) the libretro path uses — dark recessed
 * discs, light D-pad cross, light-gray round ABXY, L/R shoulders, mic and
 * lid-close secondaries — hosted in a [PadKit] whose input events go to the
 * activity's [onPadInputEvent] (mapped onto [me.magnum.melonds.MelonEmulator]
 * there). The pads read the same saved touch settings as the libretro path
 * (scale/margins under the MELONDS portrait key), so a user's pad sizing
 * carries over. The fast-forward control is a compact »» round button at
 * the POWER row's left end (slice 7: the full pill used to sit centered
 * at the deck's bottom, crowding the mic/×/☰ secondaries and the B disc
 * above them), and the FPS readout is a small muted status line at the
 * deck's bottom center (slice 7: it used to ride the POWER row's right
 * end, where the long text strip crowded the R shoulder) — both clear of
 * every key now. The activity is portrait-locked (see the manifest), so
 * this layout never has to survive a rotation-driven surface re-creation.
 *
 * Geometry comes from [GameScreenLayout.computePortraitScreenGeometry] with
 * the same side/top margins as the libretro shell, so the two paths render
 * the same shell on the same phone.
 */
@Composable
fun MelonDSGameShellContent(
    surfaceView: GLSurfaceView,
    fpsText: String,
    fastForwardEnabled: Boolean,
    onToggleFastForward: () -> Unit,
    onPadInputEvent: (List<InputEvent>) -> Unit,
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
            val screenGeometry =
                GameScreenLayout.computePortraitScreenGeometry(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    cutoutTop = cutoutTop,
                    dualScreen = true,
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
                    screenRect = viewportPosition,
                    dualScreen = false,
                    holeInflate = 8.dp,
                )

                GameViewportShellBox(
                    frameMode = ShellFrameMode.DUAL,
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

                // Hinge details on the strip the viewport box paints across the
                // seam, drawn after it so they sit on top of the frame.
                HandheldHingeBand(
                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_HINGE_BAND),
                )

                // Lower body: the shell plate with the POWER row (carrying
                // the compact fast-forward button at its left end), drawn
                // first so the pads sit on it.
                Box(
                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                ) {
                    HandheldShellBody(
                        modifier = Modifier.fillMaxSize(),
                        fastForwardEnabled = fastForwardEnabled,
                        onToggleFastForward = onToggleFastForward,
                    )
                }

                // The v3.3 pads, direct ConstraintLayout children like on the
                // libretro path, in the dark recessed-socket theme.
                CompositionLocalProvider(
                    LocalLemuroidPadTheme provides
                        LemuroidPadTheme(
                            level1FillOverride = Color(0f, 0f, 0f, 0.30f),
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

                // FPS readout: a small muted status line at the deck's
                // bottom center, in the gap between the pads' bottom-row
                // secondaries (mic/× left, ☰ right). Plain text, not a
                // button — it shows the honest presented/engine split
                // ("显示 60 · 引擎 60 FPS") and touches no key.
                androidx.compose.material3.Text(
                    text = fpsText,
                    modifier = Modifier.layoutId(MELONDS_FPS_TEXT),
                    color = ShellTheme.detailLabel.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Portrait constraint set for the melonDS shell: the viewport gets its exact
 * geometry size, the hinge band sits over the viewport's vertical center
 * (the seam between the two DS screens), the lower body takes everything
 * below the viewport, and the pads take the deck's bottom in a spread
 * chain — the same pad constraints the libretro portrait set uses, so the
 * v3.3 deck lands in the same place on the same phone.
 */
private fun melonDSShellConstraintSet(
    screenGeometry: GameScreenLayout.PortraitScreenGeometry,
): ConstraintSet =
    ConstraintSet {
        val shellBody = createRefFor(GameScreenLayout.CONSTRAINTS_SHELL_BODY)
        val gameView = createRefFor(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
        val hingeBand = createRefFor(GameScreenLayout.CONSTRAINTS_HINGE_BAND)
        val bottomContainer = createRefFor(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER)
        val leftPad = createRefFor(GameScreenLayout.CONSTRAINTS_LEFT_PAD)
        val rightPad = createRefFor(GameScreenLayout.CONSTRAINTS_RIGHT_PAD)
        val fpsTextRef = createRefFor(MELONDS_FPS_TEXT)

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

        constrain(hingeBand) {
            width = Dimension.value(screenGeometry.contentWidth)
            height = Dimension.value(GameScreenLayout.HINGE_BAND_HEIGHT)
            absoluteLeft.linkTo(gameView.absoluteLeft)
            absoluteRight.linkTo(gameView.absoluteRight)
            top.linkTo(
                gameView.top,
                margin =
                    screenGeometry.contentHeight / 2 -
                        GameScreenLayout.HINGE_BAND_HEIGHT / 2,
            )
        }

        constrain(bottomContainer) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            absoluteLeft.linkTo(parent.absoluteLeft)
            absoluteRight.linkTo(parent.absoluteRight)
            top.linkTo(leftPad.top)
            bottom.linkTo(parent.bottom)
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

        constrain(fpsTextRef) {
            absoluteLeft.linkTo(parent.absoluteLeft)
            absoluteRight.linkTo(parent.absoluteRight)
            bottom.linkTo(parent.bottom, margin = 26.dp)
        }
    }

/** Height the viewport sizing reserves for the controls deck below it. */
private val MELONDS_DECK_RESERVE = 300.dp

/** Layout id of the FPS status line at the deck's bottom center. */
private const val MELONDS_FPS_TEXT = "melondsFpsText"
