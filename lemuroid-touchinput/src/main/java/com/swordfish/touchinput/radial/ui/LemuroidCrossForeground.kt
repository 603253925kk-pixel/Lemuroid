package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import gg.padkit.ui.DefaultCrossForeground

private val CrossFill = Color(0xFF35353B)
private val CrossFillPressed = Color(0xFF17171B)
private val CrossIcon = Color(0xFFDDD8CE)
private val CrossIconPressed = Color(0xFFFFFFFF)

/**
 * Handheld-style cross: the four direction dials and the center pivot share
 * the same dark fill and sit edge to edge, so together they read as one
 * solid D-pad cross instead of four separate round buttons.
 */
@Composable
fun LemuroidCrossForeground(
    allowDiagonals: Boolean,
    directionState: State<Offset>,
) {
    DefaultCrossForeground(
        modifier = Modifier.fillMaxSize(),
        directionState = directionState,
        allowDiagonals = allowDiagonals,
        leftDial = {
            LemuroidCrossArmButton(it, Icons.Default.KeyboardArrowLeft)
        },
        rightDial = {
            LemuroidCrossArmButton(it, Icons.Default.KeyboardArrowRight)
        },
        topDial = {
            LemuroidCrossArmButton(it, Icons.Default.KeyboardArrowUp)
        },
        bottomDial = {
            LemuroidCrossArmButton(it, Icons.Default.KeyboardArrowDown)
        },
        foregroundComposite = {
            LemuroidCrossCenter(it)
        },
    )
}

@Composable
private fun LemuroidCrossArmButton(
    pressedState: State<Boolean>,
    imageVector: ImageVector,
) {
    GlassSurface(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        cornerRadius = 8.dp,
        fillColor = if (pressedState.value) CrossFillPressed else CrossFill,
        shadowColor = Color.Black.copy(alpha = 0.35f),
        shadowWidth = 3.dp,
        content = {
            Icon(
                modifier = Modifier.size(maxWidth * 0.5f, maxHeight * 0.5f),
                imageVector = imageVector,
                contentDescription = "",
                tint = if (pressedState.value) CrossIconPressed else CrossIcon,
            )
        },
    )
}

@Composable
private fun LemuroidCrossCenter(pressedState: State<Boolean>) {
    GlassSurface(
        modifier = Modifier.fillMaxSize().padding(2.dp),
        fillColor = if (pressedState.value) CrossFillPressed else CrossFill,
        shadowColor = Color.Black.copy(alpha = 0.35f),
        shadowWidth = 3.dp,
    )
}
