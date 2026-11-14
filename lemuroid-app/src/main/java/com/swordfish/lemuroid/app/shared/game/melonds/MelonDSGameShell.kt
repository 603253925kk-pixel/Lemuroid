package com.swordfish.lemuroid.app.shared.game.melonds

import android.opengl.GLSurfaceView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import com.swordfish.lemuroid.app.mobile.feature.game.GameScreenLayout
import com.swordfish.lemuroid.app.mobile.feature.game.GameViewportShellBox
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldHingeBand
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldShellBody
import com.swordfish.lemuroid.app.mobile.feature.game.HandheldShellFullBody
import com.swordfish.lemuroid.app.mobile.feature.game.ShellFrameMode

/**
 * Portrait handheld-shell layout for the vendored melonDS engine (M4).
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
 * The existing View controls deck (FPS pill, L/R, D-pad, ABXY, SELECT /
 * START / fast-forward / exit) is hosted in the shell's lower body, over
 * [HandheldShellBody]; input handling is untouched (touch goes to the
 * surface's own listener, buttons still call [me.magnum.melonds.MelonEmulator]
 * directly). The activity is portrait-locked (see the manifest), so this
 * layout never has to survive a rotation-driven surface re-creation.
 *
 * Geometry comes from [GameScreenLayout.computePortraitScreenGeometry] with
 * the same side/top margins as the libretro shell, so the two paths render
 * the same shell on the same phone.
 */
@Composable
fun MelonDSGameShellContent(
    surfaceView: GLSurfaceView,
    controlsDeck: View,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        val density = LocalDensity.current
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
            // Absurdly small display: fall back to a bare surface + deck
            // instead of crashing on a null geometry. Never expected on a
            // real phone.
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                AndroidView(
                    factory = { surfaceView },
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
                AndroidView(
                    factory = { controlsDeck },
                    modifier = Modifier.fillMaxSize().weight(1f),
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

            // Lower body: the shell plate with the POWER row, with the
            // existing View deck hosted over it.
            Box(
                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
            ) {
                HandheldShellBody(
                    modifier = Modifier.fillMaxSize(),
                )
                AndroidView(
                    factory = { controlsDeck },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Portrait constraint set for the melonDS shell: the viewport gets its exact
 * geometry size, the hinge band sits over the viewport's vertical center
 * (the seam between the two DS screens), and the lower body takes everything
 * below the viewport. Deliberately smaller than the libretro set — there are
 * no libretro touch pads to chain here; the deck is a single View.
 */
private fun melonDSShellConstraintSet(
    screenGeometry: GameScreenLayout.PortraitScreenGeometry,
): ConstraintSet =
    ConstraintSet {
        val shellBody = createRefFor(GameScreenLayout.CONSTRAINTS_SHELL_BODY)
        val gameView = createRefFor(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
        val hingeBand = createRefFor(GameScreenLayout.CONSTRAINTS_HINGE_BAND)
        val bottomContainer = createRefFor(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER)

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
            top.linkTo(gameView.bottom)
            bottom.linkTo(parent.bottom)
        }
    }

/** Height the viewport sizing reserves for the controls deck below it. */
private val MELONDS_DECK_RESERVE = 300.dp
