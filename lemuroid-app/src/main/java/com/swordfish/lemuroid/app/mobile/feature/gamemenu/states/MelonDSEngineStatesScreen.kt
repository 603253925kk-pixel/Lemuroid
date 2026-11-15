package com.swordfish.lemuroid.app.mobile.feature.gamemenu.states

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink

/**
 * Slot list for the vendored melonDS engine's own savestates. Same rows as
 * [GameMenuStatesScreen] (title + date), without the preview thumbnail the
 * libretro states carry — the engine does not capture one with its states.
 */
@Composable
fun MelonDSEngineStatesScreen(
    viewModel: MelonDSEngineStatesViewModel,
    onStateClicked: (Int) -> Unit,
) {
    val state = viewModel.uiStates.collectAsState(initial = MelonDSEngineStatesViewModel.State())

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        state.value.entries.forEachIndexed { index, entry ->
            LemuroidSettingsMenuLink(
                title = { Text(text = entry.title) },
                subtitle = { Text(text = entry.description) },
                enabled = entry.enabled,
                onClick = { onStateClicked(index) },
            )
        }
    }
}
