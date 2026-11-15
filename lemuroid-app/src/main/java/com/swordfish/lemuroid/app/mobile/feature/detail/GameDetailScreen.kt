package com.swordfish.lemuroid.app.mobile.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Game detail page, Pixel-style template layout: a big hero card (cover +
 * system chip), title, solid "开始游戏" button, play-time / saves summary
 * cards, and a "游戏信息" section.
 *
 * Only data the app actually has is shown: play time, save states, system
 * and developer come from the library database. Release year and region do
 * not exist in the Game entity (no metadata source for them), so those rows
 * are omitted rather than invented.
 */
@Composable
fun GameDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: GameDetailViewModel,
    onPlayClick: (Game) -> Unit,
) {
    val state = viewModel.getViewStates().collectAsState(GameDetailViewModel.UIState()).value
    val game = state.game
    if (game == null) {
        if (state.loading) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }
    GameDetailScreen(
        modifier = modifier,
        game = game,
        saveSlotsUsed = state.saveSlotsUsed,
        hasAutoSave = state.hasAutoSave,
        onPlayClick = onPlayClick,
    )
}

@Composable
private fun GameDetailScreen(
    modifier: Modifier = Modifier,
    game: Game,
    saveSlotsUsed: Int,
    hasAutoSave: Boolean,
    onPlayClick: (Game) -> Unit,
) {
    val context = LocalContext.current
    val system =
        remember(game.systemId) {
            GameSystem.findById(game.systemId)
        }
    val systemShort = context.getString(system.shortTitleResId)
    val systemFull = context.getString(system.titleResId)
    val playTimeText =
        remember(game.totalPlayTimeMillis) {
            GameUtils.formatPlayTime(game.totalPlayTimeMillis)
        }

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero card: cover art, system chip overlaid at the bottom.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                LemuroidGameImage(game = game)
                Text(
                    text = systemShort,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            text = game.displayName(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onPlayClick(game) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            shape = CircleShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Text(
                text = stringResource(id = R.string.game_detail_play),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailSummaryCard(
                modifier = Modifier.weight(1f),
                label = stringResource(id = R.string.game_detail_play_time),
                value =
                    if (game.totalPlayTimeMillis > 0) {
                        playTimeText
                    } else {
                        stringResource(id = R.string.game_detail_no_play_time)
                    },
            )
            DetailSummaryCard(
                modifier = Modifier.weight(1f),
                label = stringResource(id = R.string.game_detail_saves),
                value =
                    when {
                        saveSlotsUsed > 0 -> {
                            val slotsText =
                                stringResource(id = R.string.game_detail_slot_saves, saveSlotsUsed)
                            if (hasAutoSave) {
                                "$slotsText · ${stringResource(id = R.string.game_detail_auto_save)}"
                            } else {
                                slotsText
                            }
                        }
                        hasAutoSave -> stringResource(id = R.string.game_detail_auto_save)
                        else -> stringResource(id = R.string.game_detail_no_saves)
                    },
            )
        }

        Text(
            text = stringResource(id = R.string.game_detail_info),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        DetailInfoRow(
            label = stringResource(id = R.string.game_detail_platform),
            value = systemFull,
        )
        if (game.developer?.isNotBlank() == true) {
            DetailInfoRow(
                label = stringResource(id = R.string.game_detail_developer),
                value = game.developer!!,
            )
        }
        // No release-year / region rows: the Game entity carries neither
        // field and there is no metadata source for them — omitted rather
        // than invented.
    }
}

@Composable
private fun DetailSummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
