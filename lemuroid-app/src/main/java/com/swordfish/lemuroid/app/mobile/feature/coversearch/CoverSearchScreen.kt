package com.swordfish.lemuroid.app.mobile.feature.coversearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.coversearch.LibretroCoverSearch.CoverCandidate
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun CoverSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: CoverSearchViewModel,
    onSetCustomCoverUri: (Game, String) -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.appliedCoverUri) {
        val appliedUri = state.appliedCoverUri ?: return@LaunchedEffect
        val game = state.game ?: return@LaunchedEffect
        onSetCustomCoverUri(game, appliedUri)
        viewModel.onCoverApplied()
        onDone()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onQueryChange(it) },
                label = { Text(text = stringResource(R.string.cover_search_keyword_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.search()
                        },
                    ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.search()
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(text = stringResource(R.string.cover_search_search))
            }
        }

        Text(
            text = stringResource(R.string.cover_search_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (state.applyError) {
            Text(
                text = stringResource(R.string.cover_search_apply_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.libraryCovers.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cover_library_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.libraryCovers, key = { it.id }) { item ->
                    AsyncImage(
                        model = viewModel.libraryCoverUri(item),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .width(72.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectLibraryCover(item) },
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 16.dp),
        ) {
            when {
                state.isLoadingList -> CoverSearchCenteredMessage {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.cover_search_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                state.listError -> CoverSearchCenteredMessage {
                    Text(
                        text = stringResource(R.string.cover_search_error),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { viewModel.search() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = stringResource(R.string.cover_search_retry))
                    }
                }
                state.candidates.isEmpty() -> CoverSearchCenteredMessage {
                    Text(
                        text = stringResource(R.string.cover_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.candidates, key = { it.fileName }) { candidate ->
                            CoverCandidateCell(
                                candidate = candidate,
                                onClick = { viewModel.selectCandidate(candidate) },
                            )
                        }
                    }
            }

            if (state.isApplying) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.cover_search_applying),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverSearchCenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
private fun CoverCandidateCell(
    candidate: CoverCandidate,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(4.dp),
    ) {
        AsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(candidate.imageUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = candidate.displayName,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = candidate.displayName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
