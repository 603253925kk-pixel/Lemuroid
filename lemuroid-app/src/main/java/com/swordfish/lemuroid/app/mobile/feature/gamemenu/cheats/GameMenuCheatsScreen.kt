package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.lib.library.CheatsManager
import com.swordfish.lemuroid.lib.library.db.entity.Cheat

@Composable
fun GameMenuCheatsScreen(
    viewModel: GameMenuCheatsViewModel,
    onCheatsChanged: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val defaultCheatName =
        stringResource(R.string.game_menu_cheats_default_name, (state.cheats.size + 1).toString())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCheat by remember { mutableStateOf<Cheat?>(null) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.game_menu_cheats_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )

        if (state.cheats.isEmpty()) {
            Text(
                text = stringResource(R.string.game_menu_cheats_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
            )
        }

        state.cheats.forEach { cheat ->
            CheatRow(
                cheat = cheat,
                onEdit = { editingCheat = cheat },
                onEnabledChange = { enabled ->
                    viewModel.setCheatEnabled(cheat, enabled)
                    onCheatsChanged()
                },
                onDelete = {
                    viewModel.deleteCheat(cheat)
                    onCheatsChanged()
                },
            )
        }

        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(R.string.game_menu_cheats_add)) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.game_menu_cheats_add),
                )
            },
            onClick = { showAddDialog = true },
        )
    }

    if (showAddDialog) {
        CheatEditDialog(
            title = stringResource(R.string.game_menu_cheats_add),
            initialName = "",
            initialCode = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, code ->
                viewModel.addCheat(name.ifBlank { defaultCheatName }, code)
                onCheatsChanged()
                showAddDialog = false
            },
        )
    }

    editingCheat?.let { cheat ->
        CheatEditDialog(
            title = stringResource(R.string.game_menu_cheats_edit),
            initialName = cheat.name,
            initialCode = cheat.code,
            onDismiss = { editingCheat = null },
            onSave = { name, code ->
                viewModel.updateCheat(cheat, name.ifBlank { cheat.name }, code)
                onCheatsChanged()
                editingCheat = null
            },
        )
    }
}

@Composable
private fun CheatRow(
    cheat: Cheat,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable { onEdit() }
                    .padding(vertical = 8.dp),
        ) {
            Text(
                text = cheat.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = cheat.code,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = cheat.enabled,
            onCheckedChange = onEnabledChange,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.game_menu_cheats_delete),
            )
        }
    }
}

@Composable
private fun CheatEditDialog(
    title: String,
    initialName: String,
    initialCode: String,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var code by remember { mutableStateOf(initialCode) }
    var showCodeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.game_menu_cheats_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        showCodeError = false
                    },
                    label = { Text(text = stringResource(R.string.game_menu_cheats_code)) },
                    isError = showCodeError,
                    minLines = 3,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
                if (showCodeError) {
                    Text(
                        text = stringResource(R.string.game_menu_cheats_invalid_code),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (CheatsManager.isValidCode(code)) {
                        onSave(name.trim(), code.trim())
                    } else {
                        showCodeError = true
                    }
                },
            ) {
                Text(text = stringResource(R.string.game_menu_cheats_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
