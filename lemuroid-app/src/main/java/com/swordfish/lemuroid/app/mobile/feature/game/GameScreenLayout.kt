package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.ui.unit.Dp
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
    // seam as the hinge band. Kept very narrow on purpose: at the DS
    // viewport's scale (~1.46dp per game pixel on a 375dp-wide viewport)
    // this covers only ~3 game pixels on either side of the seam, so the
    // bottom text line of the top screen stays fully readable. The previous
    // 24dp band ate ~8 game pixels per side and cut that line in half.
    //
    // NOTE: this is the libretro path's overlay band. The melonDS path
    // does not use it anymore: it renders two genuinely separate windows
    // with a real gap between them (see DUAL_SCREEN_HINGE_GAP below), so
    // no game pixel is ever covered.
    val HINGE_BAND_HEIGHT = 10.dp

    // Real hinge gap between the two DS screen windows on the melonDS
    // path (see computePortraitDualScreenGeometry): the two 4:3 windows
    // are separated by this much body, with the hinge barrel drawn in
    // the middle of it. ~10% of one screen's height — the reference
    // DS Lite shell reads as two separate screens, not one long screen
    // with a line drawn across it. The gap is never mapped to texture
    // content: each window shows its full 256x192, and the gap shows
    // only body. The barrel capsule drawn in the gap is 12dp, leaving
    // 8dp of black (the screens' own bezels) above and below it.
    val DUAL_SCREEN_HINGE_GAP = 28.dp

    // Height of the Nintendo logo strip above the top screen window on
    // the melonDS path (a raised body band carrying the Nintendo oval
    // and the power/charging LEDs, like the reference shell). Folded
    // into the dual-screen geometry's top margin.
    val DUAL_SCREEN_LOGO_STRIP_HEIGHT = 12.dp

    // Side and top margins of the portrait screen block inside the body.
    val SCREEN_SIDE_MARGIN = 18.dp
    val SCREEN_TOP_MARGIN = 18.dp

    /**
     * Deterministic size of the portrait game viewport, computed from the
     * display size by [computePortraitScreenGeometry]. The renderer
     * aspect-fits the core frame into the viewport, so the viewport rect
     * itself must match the frame aspect exactly: 2:3 for the stacked DS
     * frame, 3:2 for GBA. The placeholder is sized to exactly
     * [contentWidth] x [contentHeight] and carries no insets padding of its
     * own; [cutoutTop] is folded into the top margin instead. (v3.2 added
     * the cutout to the height *and* padded the placeholder by it, which
     * made the reported viewport ~2x the cutout too tall: the renderer
     * centered the frame inside it, leaving a letterbox band at the top of
     * the upper window and below the lower one.)
     */
    data class PortraitScreenGeometry(
        val contentWidth: Dp,
        val contentHeight: Dp,
        val cutoutTop: Dp,
        val sideMargin: Dp,
        val topMargin: Dp,
    )

    /**
     * Computes the portrait viewport size for the dual-screen (DS) and GBA
     * shells from the available display size. Screens are sized from the
     * width (each DS screen is 4:3, both equal; the GBA screen is 3:2) and
     * capped so the control deck below keeps at least [deckReserve] of
     * height. Returns null when the display is too small to bother.
     */
    fun computePortraitScreenGeometry(
        maxWidth: Dp,
        maxHeight: Dp,
        cutoutTop: Dp,
        dualScreen: Boolean,
        deckReserve: Dp,
    ): PortraitScreenGeometry? {
        var contentWidth = maxWidth - SCREEN_SIDE_MARGIN * 2
        var contentHeight =
            if (dualScreen) {
                contentWidth * 3 / 2
            } else {
                contentWidth * 2 / 3
            }

        val maxContentHeight = maxHeight - SCREEN_TOP_MARGIN - cutoutTop - deckReserve
        if (contentHeight > maxContentHeight) {
            contentHeight = maxContentHeight
            contentWidth =
                if (dualScreen) {
                    contentHeight * 2 / 3
                } else {
                    contentHeight * 3 / 2
                }
        }

        if (contentWidth <= 0.dp || contentHeight <= 0.dp) return null

        return PortraitScreenGeometry(
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            cutoutTop = cutoutTop,
            sideMargin = SCREEN_SIDE_MARGIN,
            topMargin = SCREEN_TOP_MARGIN,
        )
    }

    /**
     * Size of the melonDS path's two-window portrait layout, computed by
     * [computePortraitDualScreenGeometry]. Unlike [PortraitScreenGeometry]
     * (one 2:3 block for the stacked DS frame), this is two genuinely
     * separate 4:3 windows: [windowHeight] is one screen's height,
     * [hingeGap] the real body gap between them, and [contentHeight] is
     * the whole viewport (2 x windowHeight + hingeGap) that the surface
     * and the frame box are sized to. Each window shows its full 256x192
     * — no cropping, no squashing — and the gap is never mapped to
     * texture content (the renderer leaves it undrawn; the shell's
     * hinge barrel covers it). [topMargin] already includes the Nintendo
     * logo strip above the top window.
     */
    data class DualScreenGeometry(
        val contentWidth: Dp,
        val windowHeight: Dp,
        val hingeGap: Dp,
        val contentHeight: Dp,
        val cutoutTop: Dp,
        val sideMargin: Dp,
        val topMargin: Dp,
    )

    /**
     * Computes the melonDS path's two-window portrait layout from the
     * available display size. The windows are sized from the width (each
     * DS screen is 4:3, both equal) and capped so the control deck below
     * keeps at least [deckReserve] of height; the hinge gap is fixed —
     * when the display is short the windows shrink proportionally, the
     * gap never eats screen content. Returns null when the display is
     * too small to bother. The libretro path keeps using
     * [computePortraitScreenGeometry] (one 2:3 block) and is untouched.
     */
    fun computePortraitDualScreenGeometry(
        maxWidth: Dp,
        maxHeight: Dp,
        cutoutTop: Dp,
        deckReserve: Dp,
    ): DualScreenGeometry? {
        val topMargin = SCREEN_TOP_MARGIN + DUAL_SCREEN_LOGO_STRIP_HEIGHT
        var contentWidth = maxWidth - SCREEN_SIDE_MARGIN * 2
        var windowHeight = contentWidth * 3 / 4
        var contentHeight = windowHeight * 2 + DUAL_SCREEN_HINGE_GAP

        val maxContentHeight = maxHeight - topMargin - cutoutTop - deckReserve
        if (contentHeight > maxContentHeight) {
            contentHeight = maxContentHeight
            windowHeight = (contentHeight - DUAL_SCREEN_HINGE_GAP) / 2
            contentWidth = windowHeight * 4 / 3
        }

        if (contentWidth <= 0.dp || contentHeight <= 0.dp || windowHeight <= 0.dp) return null

        return DualScreenGeometry(
            contentWidth = contentWidth,
            windowHeight = windowHeight,
            hingeGap = DUAL_SCREEN_HINGE_GAP,
            contentHeight = contentHeight,
            cutoutTop = cutoutTop,
            sideMargin = SCREEN_SIDE_MARGIN,
            topMargin = topMargin,
        )
    }

    fun buildConstraintSet(
        isLandscape: Boolean,
        allowTouchOverlay: Boolean,
        portraitGameViewRatio: String? = null,
        portraitScreenGeometry: PortraitScreenGeometry? = null,
    ): ConstraintSet {
        return when {
            !isLandscape -> buildConstraintSetPortrait(portraitGameViewRatio, portraitScreenGeometry)
            allowTouchOverlay -> buildConstraintSetLandscape()
            else -> buildConstraintSetLandscapeNoOverlay()
        }
    }

    private fun buildConstraintSetPortrait(
        gameViewRatio: String? = null,
        screenGeometry: PortraitScreenGeometry? = null,
    ): ConstraintSet {
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
                if (screenGeometry != null) {
                    // Geometry-driven shells (DS, GBA): the viewport gets an
                    // exact size computed from the display, so the renderer
                    // fills it with no letterbox and the shell frame drawn
                    // inside the placeholder lines up by construction. The
                    // display cutout is part of the top margin, never of the
                    // height: the placeholder must be exactly the content
                    // rect it reports as the GL viewport.
                    width = Dimension.value(screenGeometry.contentWidth)
                    height = Dimension.value(screenGeometry.contentHeight)
                    top.linkTo(
                        parent.top,
                        margin = screenGeometry.topMargin + screenGeometry.cutoutTop,
                    )
                    absoluteLeft.linkTo(parent.absoluteLeft)
                    absoluteRight.linkTo(parent.absoluteRight)
                } else {
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
                if (screenGeometry != null) {
                    // Place the band exactly over the seam of the content
                    // rect: the placeholder's top plus half the content
                    // height (the placeholder is exactly the content rect,
                    // no inset inside it). All values come from the same
                    // geometry as the viewport, so the band cannot drift.
                    width = Dimension.value(screenGeometry.contentWidth)
                    height = Dimension.value(HINGE_BAND_HEIGHT)
                    absoluteLeft.linkTo(gameView.absoluteLeft)
                    absoluteRight.linkTo(gameView.absoluteRight)
                    top.linkTo(
                        gameView.top,
                        margin =
                            screenGeometry.contentHeight / 2 -
                                HINGE_BAND_HEIGHT / 2,
                    )
                } else {
                    width = Dimension.fillToConstraints
                    height = Dimension.value(HINGE_BAND_HEIGHT)
                    absoluteLeft.linkTo(gameView.absoluteLeft)
                    absoluteRight.linkTo(gameView.absoluteRight)
                    top.linkTo(gameView.top)
                    bottom.linkTo(gameView.bottom)
                    verticalBias = 0.5f
                }
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
