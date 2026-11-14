package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.DeckColors
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.settings.DeckSettingIcon
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val state =
        viewModel.uiState
            .collectAsState(SettingsViewModel.State())
            .value

    val scanInProgress =
        viewModel.directoryScanInProgress
            .collectAsState(false)
            .value

    val indexingInProgress =
        viewModel.indexingInProgress
            .collectAsState(false)
            .value

    LemuroidSettingsPage(modifier = modifier) {
        RomsSettings(
            state = state,
            onChangeFolder = { viewModel.changeLocalStorageFolder() },
            indexingInProgress = indexingInProgress,
            scanInProgress = scanInProgress,
        )
        GeneralSettings()
        InputSettings(navController = navController)
        MiscSettings(
            indexingInProgress = indexingInProgress,
            isSaveSyncSupported = state.isSaveSyncSupported,
            navController = navController,
        )
    }
}

@Composable
private fun MiscSettings(
    indexingInProgress: Boolean,
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_misc)) },
    ) {
        if (isSaveSyncSupported) {
            LemuroidSettingsMenuLink(
                icon = { DeckSettingIcon(Icons.Filled.Sync, DeckColors.IconOrange) },
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
        LemuroidSettingsMenuLink(
            icon = { DeckSettingIcon(Icons.Filled.Memory, DeckColors.IconPurple) },
            title = { Text(text = stringResource(id = R.string.settings_title_open_cores_selection)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_open_cores_selection))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
        )
        LemuroidSettingsMenuLink(
            icon = { DeckSettingIcon(Icons.Filled.Memory, DeckColors.IconRed) },
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        LemuroidSettingsMenuLink(
            icon = { DeckSettingIcon(Icons.Filled.Bolt, DeckColors.IconTeal) },
            title = { Text(text = stringResource(id = R.string.settings_title_advanced_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_advanced_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ADVANCED) },
        )
    }
}

@Composable
private fun InputSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_haptic_feedback_mode,
                    "press",
                    stringListResource(R.array.pref_key_haptic_feedback_mode_values),
                ),
            title = {
                Text(text = stringResource(id = R.string.settings_title_enable_touch_feedback))
            },
            items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
        )
        LemuroidSettingsMenuLink(
            icon = { DeckSettingIcon(Icons.Filled.Gamepad, DeckColors.IconOrange) },
            title = { Text(text = stringResource(id = R.string.settings_title_gamepad_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_gamepad_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_INPUT_DEVICES) },
        )
    }
}

@Composable
private fun GeneralSettings() {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            icon = { DeckSettingIcon(Icons.Filled.Save, DeckColors.IconGreen) },
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
        )
        LemuroidSettingsSwitch(
            state = immersiveMode,
            icon = { DeckSettingIcon(Icons.Filled.Fullscreen, DeckColors.IconTeal) },
            title = { Text(text = stringResource(id = R.string.settings_title_immersive_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_immersive_mode)) },
        )
        LemuroidSettingsSwitch(
            state = hdMode,
            icon = { DeckSettingIcon(Icons.Filled.HighQuality, DeckColors.IconBlue) },
            title = { Text(text = stringResource(id = R.string.settings_title_hd_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_mode)) },
        )
        LemuroidSettingsSlider(
            enabled = hdMode.value,
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_hd_mode_quality),
                    default = 2,
                ),
            steps = 1,
            valueRange = 0f..2f,
            title = { Text(text = stringResource(R.string.settings_title_hd_quality)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_quality)) },
        )
        LemuroidSettingsList(
            enabled = !hdMode.value,
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
        LemuroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_viewport_alignment,
                    "center",
                    stringListResource(R.array.pref_key_viewport_alignment_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_viewport_alignment)) },
            items = stringListResource(R.array.pref_key_viewport_alignment_display_names),
        )
    }
}

@Composable
private fun RomsSettings(
    state: SettingsViewModel.State,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current

    val currentDirectory = state.currentDirectory
    val emptyDirectory = stringResource(R.string.none)

    val currentDirectoryName =
        remember(state.currentDirectory) {
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
            }.getOrNull() ?: emptyDirectory
        }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.roms)) }) {
        LemuroidSettingsMenuLink(
            icon = { DeckSettingIcon(Icons.Filled.FolderOpen, DeckColors.IconBlue) },
            title = { Text(text = stringResource(id = R.string.directory)) },
            subtitle = { Text(text = currentDirectoryName) },
            onClick = { onChangeFolder() },
            enabled = !indexingInProgress,
        )
        if (scanInProgress) {
            LemuroidSettingsMenuLink(
                icon = { DeckSettingIcon(Icons.Filled.Stop, DeckColors.IconRed) },
                title = { Text(text = stringResource(id = R.string.stop)) },
                onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
            )
        } else {
            LemuroidSettingsMenuLink(
                icon = { DeckSettingIcon(Icons.Filled.Refresh, DeckColors.IconGreen) },
                title = { Text(text = stringResource(id = R.string.rescan)) },
                onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                enabled = !indexingInProgress,
            )
        }
    }
}
