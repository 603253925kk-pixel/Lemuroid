package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension

object GameScreenLayout {
    const val CONSTRAINTS_BOTTOM_CONTAINER = "bottomContainer"
    const val CONSTRAINTS_LEFT_CONTAINER = "leftContainer"
    const val CONSTRAINTS_RIGHT_CONTAINER = "rightContainer"
    const val CONSTRAINTS_LEFT_PAD = "leftPad"
    const val CONSTRAINTS_RIGHT_PAD = "rightPad"
    const val CONSTRAINTS_GAME_VIEW = "gameView"
    const val CONSTRAINTS_GAME_CONTAINER = "gameContainer"
    const val CONSTRAINTS_SHELL_BODY = "shellBody"
    const val CONSTRAINTS_SCREEN_BEZEL = "screenBezel"
    const val CONSTRAINTS_HINGE_BAND = "hingeBand"
    const val CONSTRAINTS_SPEAKER_GRILLE = "speakerGrille"

    // Native aspect ratio of the GBA screen (240x160). A single wide screen
    // like this one should hug its bezel instead of filling the tall area
    // that suits the stacked dual screens of the DS.
    const val GAME_VIEW_RATIO_GBA = "3:2"

    // Height of the body strip left between the two DS screen windows. The
    // core stacks both screens in one viewport with the seam at its vertical
    // center, and the shell fill leaves exactly this much body across the
    // seam as the hinge band. Kept narrow on purpose: it may only cover the
    // rows right at the seam, never a meaningful part of either screen.
    val HINGE_BAND_HEIGHT = 24.dp

    fun buildConstraintSet(
        isLandscape: Boolean,
        allowTouchOverlay: Boolean,
        portraitGameViewRatio: String? = null,
    ): ConstraintSet {
        return when {
            !isLandscape -> buildConstraintSetPortrait(portraitGameViewRatio)
            allowTouchOverlay -> buildConstraintSetLandscape()
            else -> buildConstraintSetLandscapeNoOverlay()
        }
    }

    private fun buildConstraintSetPortrait(gameViewRatio: String? = null): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val leftPad = createRefFor(CONSTRAINTS_LEFT_PAD)
            val rightPad = createRefFor(CONSTRAINTS_RIGHT_PAD)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)
            val bottomContainer = createRefFor(CONSTRAINTS_BOTTOM_CONTAINER)
            val shellBody = createRefFor(CONSTRAINTS_SHELL_BODY)
            val screenBezel = createRefFor(CONSTRAINTS_SCREEN_BEZEL)
            val hingeBand = createRefFor(CONSTRAINTS_HINGE_BAND)
            val speakerGrille = createRefFor(CONSTRAINTS_SPEAKER_GRILLE)

            val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)

            constrain(shellBody) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
            }

            constrain(screenBezel) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                top.linkTo(parent.top, margin = 8.dp)
                absoluteLeft.linkTo(parent.absoluteLeft, margin = 8.dp)
                absoluteRight.linkTo(parent.absoluteRight, margin = 8.dp)
                bottom.linkTo(gameView.bottom, margin = 8.dp)
            }

            constrain(gameView) {
                width = Dimension.fillToConstraints
                height =
                    if (gameViewRatio != null) {
                        Dimension.ratio(gameViewRatio)
                    } else {
                        Dimension.fillToConstraints
                    }
                if (gameViewRatio != null) {
                    // Pin the screen to the top of the body; the area below it
                    // stays as shell, like on a real single-screen handheld.
                    verticalBias = 0f
                }
                top.linkTo(parent.top, margin = 18.dp)
                absoluteLeft.linkTo(parent.absoluteLeft, margin = 18.dp)
                absoluteRight.linkTo(parent.absoluteRight, margin = 18.dp)
                bottom.linkTo(leftPad.top)
            }

            constrain(bottomContainer) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                top.linkTo(leftPad.top)
                bottom.linkTo(parent.bottom)
            }

            // Decorative details on the hinge band: centered on the game
            // view, which is where the seam between the two DS screens sits.
            constrain(hingeBand) {
                width = Dimension.fillToConstraints
                height = Dimension.value(HINGE_BAND_HEIGHT)
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
                verticalBias = 0.5f
            }

            // Speaker grille for single-screen shells: on the body below
            // the screen, clear of the game view.
            constrain(speakerGrille) {
                width = Dimension.wrapContent
                height = Dimension.wrapContent
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                top.linkTo(gameView.bottom, margin = 14.dp)
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

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }
        }
    }

    private fun buildConstraintSetLandscape(): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val leftPad = createRefFor(CONSTRAINTS_LEFT_PAD)
            val rightPad = createRefFor(CONSTRAINTS_RIGHT_PAD)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)

            val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)

            constrain(gameView) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                top.linkTo(parent.top)
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                bottom.linkTo(parent.bottom)
            }

            constrain(gamePadChain) {
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
            }

            constrain(rightPad) {
                verticalBias = 1.0f
                bottom.linkTo(parent.bottom)
                top.linkTo(parent.top)
            }

            constrain(leftPad) {
                verticalBias = 1.0f
                bottom.linkTo(parent.bottom)
                top.linkTo(parent.top)
            }

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }
        }
    }

    private fun buildConstraintSetLandscapeNoOverlay(): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val leftPad = createRefFor(CONSTRAINTS_LEFT_PAD)
            val rightPad = createRefFor(CONSTRAINTS_RIGHT_PAD)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)
            val leftContainer = createRefFor(CONSTRAINTS_LEFT_CONTAINER)
            val rightContainer = createRefFor(CONSTRAINTS_RIGHT_CONTAINER)

            constrain(leftPad) {
                absoluteLeft.linkTo(parent.absoluteLeft)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.wrapContent // Takes as much space as needed
            }

            constrain(gameView) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                absoluteLeft.linkTo(leftPad.absoluteRight)
                absoluteRight.linkTo(rightPad.absoluteLeft)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints // Expands between leftPad and rightPad
            }

            constrain(rightPad) {
                absoluteRight.linkTo(parent.absoluteRight)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.wrapContent // Takes as much space as needed
            }

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }

            constrain(leftContainer) {
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(leftPad.absoluteRight)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            }

            constrain(rightContainer) {
                absoluteRight.linkTo(parent.absoluteRight)
                absoluteLeft.linkTo(rightPad.absoluteLeft)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            }
        }
    }
}
