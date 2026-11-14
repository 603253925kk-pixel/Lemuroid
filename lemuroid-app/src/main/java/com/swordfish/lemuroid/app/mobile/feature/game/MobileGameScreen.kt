package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.LemuroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
        }

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings(LocalDensity.current, WindowInsets.displayCutout)
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val fastForwardSpeedState = viewModel.fastForwardSpeed.collectAsState(1)
        val fastForwardPreferredSpeedState = viewModel.fastForwardPreferredSpeed.collectAsState(2)

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            viewModel
                .getTouchHapticFeedbackMode()
                .collectAsState(HapticFeedbackMode.NONE)

        val padHapticFeedback =
            when (hapticFeedbackMode.value) {
                HapticFeedbackMode.NONE -> HapticFeedbackType.NONE
                HapticFeedbackMode.PRESS -> HapticFeedbackType.PRESS
                HapticFeedbackMode.PRESS_RELEASE -> HapticFeedbackType.PRESS_RELEASE
            }

        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
            val viewportPosition = remember { mutableStateOf<Rect?>(null) }

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle)
                },
            )

            val fullPos = fullScreenPosition.value
            val viewPos = viewportPosition.value

            LaunchedEffect(fullPos, viewPos) {
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (fullPos == null || viewPos == null) return@LaunchedEffect
                val viewport =
                    RectF(
                        (viewPos.left - fullPos.left) / fullPos.width,
                        (viewPos.top - fullPos.top) / fullPos.height,
                        (viewPos.right - fullPos.left) / fullPos.width,
                        (viewPos.bottom - fullPos.top) / fullPos.height,
                    )
                gameView.viewport = viewport
            }

            val isDualScreenShell =
                currentControllerConfig?.touchControllerID == TouchControllerID.DESMUME ||
                    currentControllerConfig?.touchControllerID == TouchControllerID.MELONDS
            val isGbaShell = currentControllerConfig?.touchControllerID == TouchControllerID.GBA

            // Deterministic viewport size for the DS and GBA portrait
            // shells: the renderer aspect-fits the core frame into the
            // viewport, so the viewport's content rect is given exactly the
            // frame's aspect (2:3 stacked DS, 3:2 GBA) and the shell frame
            // drawn inside the placeholder aligns by construction instead
            // of by cross-layer measurement. The deck reserve mirrors the
            // touch controls' footprint, so the pads keep their room.
            val screenGeometry =
                if (!isLandscape && (isDualScreenShell || isGbaShell)) {
                    val density = LocalDensity.current
                    val cutoutTop =
                        with(density) {
                            WindowInsets.displayCutout.getTop(density).toDp()
                        }
                    val dialSize =
                        160.dp *
                            lerp(
                                TouchControllerSettingsManager.MIN_SCALE,
                                TouchControllerSettingsManager.MAX_SCALE,
                                touchControllerSettings?.scale
                                    ?: TouchControllerSettingsManager.DEFAULT_SCALE,
                            )
                    GameScreenLayout.computePortraitScreenGeometry(
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        cutoutTop = cutoutTop,
                        dualScreen = isDualScreenShell,
                        deckReserve = maxOf(280.dp, dialSize + 120.dp),
                    )
                } else {
                    null
                }

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet =
                    GameScreenLayout.buildConstraintSet(
                        isLandscape,
                        currentControllerConfig?.allowTouchOverlay ?: true,
                        portraitGameViewRatio =
                            if (isGbaShell) {
                                GameScreenLayout.GAME_VIEW_RATIO_GBA
                            } else {
                                null
                            },
                        portraitScreenGeometry = screenGeometry,
                    ),
            ) {
                if (!isLandscape) {
                    // The shell layers are drawn over the game surface, so they
                    // must leave the game viewport (tracked in viewportPosition)
                    // unpainted or they would cover the game completely. With a
                    // computed geometry the base body only needs a generously
                    // inflated opening: the frame drawn by the viewport box
                    // itself defines the exact window edges.
                    HandheldShellFullBody(
                        modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_SHELL_BODY),
                        screenRect = viewportPosition.value,
                        dualScreen = isDualScreenShell && screenGeometry == null,
                        holeInflate = if (screenGeometry != null) 8.dp else 0.dp,
                    )
                    if (screenGeometry == null) {
                        HandheldScreenBezel(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_SCREEN_BEZEL),
                            screenRect = viewportPosition.value,
                            dualScreen = isDualScreenShell,
                        )
                        if (isDualScreenShell) {
                            HandheldHingeBand(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_HINGE_BAND),
                            )
                        }
                    }
                    if (isGbaShell) {
                        HandheldSpeakerGrille(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_SPEAKER_GRILLE),
                        )
                    }
                }

                GameViewportShellBox(
                    frameMode =
                        when {
                            screenGeometry == null -> ShellFrameMode.NONE
                            isDualScreenShell -> ShellFrameMode.DUAL
                            else -> ShellFrameMode.SINGLE
                        },
                    onPositioned = { viewportPosition.value = it },
                    // Geometry-driven shells size the placeholder to exactly
                    // the frame rect and fold the cutout into the top margin;
                    // padding it here as well would double-count the cutout
                    // and letterbox the game inside its own window.
                    applyCutoutPadding = screenGeometry == null,
                )

                if (screenGeometry != null && isDualScreenShell) {
                    // The hinge band details sit on the strip the viewport
                    // box paints across the seam, so they are emitted after
                    // it and draw on top of the frame.
                    HandheldHingeBand(
                        modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_HINGE_BAND),
                    )
                }

                val isVisible =
                    touchControllerSettings != null &&
                        currentControllerConfig != null &&
                        touchControlsVisibleState.value

                if (isVisible) {
                    CompositionLocalProvider(
                        LocalLemuroidPadTheme provides
                            if (isLandscape) {
                                LemuroidPadTheme()
                            } else {
                                // Portrait handheld shell: the primary dials
                                // sit in a dark recessed socket on the body
                                // instead of on a light glass disc.
                                LemuroidPadTheme(
                                    level1FillOverride = Color(0f, 0f, 0f, 0.30f),
                                )
                            },
                    ) {
                        if (!isLandscape) {
                            HandheldShellBody(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                            )
                        } else if (!currentControllerConfig.allowTouchOverlay) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                            )
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                            )
                        }

                        leftGamePad?.invoke(
                            this,
                            Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                            touchControllerSettings,
                        )
                        rightGamePad?.invoke(
                            this,
                            Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                            touchControllerSettings,
                        )

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            controllerConfig = currentControllerConfig,
                            touchControllerSettings = touchControllerSettings,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }

        if (!isLandscape && touchControlsVisibleState.value) {
            FastForwardButton(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                fastForwardSpeed = fastForwardSpeedState.value,
                preferredSpeed = fastForwardPreferredSpeedState.value,
                onToggle = { viewModel.toggleFastForward() },
                onSelectSpeed = { viewModel.selectFastForwardSpeed(it) },
            )
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(true)
                .value

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalLemuroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

@Composable
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    controllerConfig: ControllerConfig,
) {
    val menuPressed = viewModel.isMenuPressed().collectAsState(false)
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        LemuroidButtonPressFeedback(
            pressed = menuPressed.value,
            animationDurationMillis = MENU_LOADING_ANIMATION_MILLIS,
            icon = R.drawable.button_menu,
        )
        MenuEditTouchControls(viewModel, controllerConfig, touchControllerSettings)
    }
}

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
) {
    val showEditControls = viewModel.isEditControlShown().collectAsState(false)
    if (!showEditControls.value) return

    Dialog(onDismissRequest = { viewModel.showEditControls(false) }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuEditTouchControlRow(Icons.Default.OpenInFull, "Scale", 0f) {
                    Slider(
                        value = touchControllerSettings.scale,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(scale = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Horizontal Margin", 90f) {
                    Slider(
                        value = touchControllerSettings.marginX,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginX = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Vertical Margin", 0f) {
                    Slider(
                        value = touchControllerSettings.marginY,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginY = it),
                            )
                        },
                    )
                }
                if (controllerConfig.allowTouchRotation) {
                    MenuEditTouchControlRow(Icons.Default.RotateLeft, "Rotate", 0f) {
                        Slider(
                            value = touchControllerSettings.rotation,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(rotation = it),
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { viewModel.resetTouchControls() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_reset))
                    }
                    TextButton(
                        onClick = { viewModel.showEditControls(false) },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditTouchControlRow(
    icon: ImageVector,
    label: String,
    rotation: Float,
    slider: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            modifier = Modifier.rotate(rotation),
            imageVector = icon,
            contentDescription = label,
        )
        slider()
    }
}

/**
 * On-screen fast-forward toggle for the handheld shell, sitting in the lower
 * body area next to the start/select cluster. A tap toggles fast-forward at
 * the selected multiplier; a long press opens the multiplier menu. The state
 * comes from [BaseGameScreenViewModel], so the button, the game menu and the
 * gamepad shortcut always agree.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FastForwardButton(
    modifier: Modifier = Modifier,
    fastForwardSpeed: Int,
    preferredSpeed: Int,
    onToggle: () -> Unit,
    onSelectSpeed: (Int) -> Unit,
) {
    val isActive = fastForwardSpeed > 1
    val displayedSpeed = if (isActive) fastForwardSpeed else preferredSpeed
    var menuExpanded by remember { mutableStateOf(false) }
    val buttonDescription =
        stringResource(com.swordfish.lemuroid.R.string.game_fast_forward_button)

    Box(modifier = modifier.wrapContentSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
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
                            onLongClick = { menuExpanded = true },
                        )
                        .semantics { contentDescription = buttonDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "»»",
                    color = if (isActive) Color(0xFF2E2150) else Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "${displayedSpeed}x",
                color = ShellTheme.fastForwardLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            BaseGameScreenViewModel.FAST_FORWARD_SPEEDS.forEach { speed ->
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
