package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork

@Composable
fun MainTopBar(
    currentRoute: MainRoute,
    navController: NavHostController,
    onHelpPressed: () -> Unit,
    onUpdateQueryString: (String) -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    Column {
        LemuroidTopAppBar(
            route = currentRoute,
            navController = navController,
            mainUIState = mainUIState,
            onHelpPressed = onHelpPressed,
            onUpdateQueryString = onUpdateQueryString,
        )

        AnimatedVisibility(mainUIState.operationInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LemuroidTopAppBar(
    route: MainRoute,
    navController: NavController,
    mainUIState: MainViewModel.UiState,
    onHelpPressed: () -> Unit,
    onUpdateQueryString: (String) -> Unit,
) {
    val context = LocalContext.current
    // Pixel style: a solid surfaceContainer block, no translucency and no
    // tonal-elevation shimmer on scroll.
    val topBarColor = MaterialTheme.colorScheme.surfaceContainer

    TopAppBar(
        title = {
            if (route == MainRoute.SEARCH) {
                LemuroidSearchView(
                    mainUIState = mainUIState,
                    onUpdateQueryString = onUpdateQueryString,
                )
            } else {
                Text(text = stringResource(route.titleId))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                scrolledContainerColor = topBarColor,
                containerColor = topBarColor,
            ),
        navigationIcon = {
            AnimatedVisibility(
                visible = route.parent != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(id = R.string.back),
                    )
                }
            }
        },
        actions = {
            LemuroidTopBarActions(
                route = route,
                navController = navController,
                context = context,
                saveSyncEnabled = mainUIState.saveSyncEnabled,
                onHelpPressed = onHelpPressed,
                operationsInProgress = mainUIState.operationInProgress,
            )
        },
    )
}

@Composable
fun LemuroidTopBarActions(
    route: MainRoute,
    navController: NavController,
    context: Context,
    saveSyncEnabled: Boolean,
    operationsInProgress: Boolean,
    onHelpPressed: () -> Unit,
) {
    Row {
        IconButton(
            onClick = { onHelpPressed() },
        ) {
            Icon(
                Icons.Outlined.Info,
                stringResource(R.string.mobile_settings_help),
            )
        }
        if (saveSyncEnabled) {
            IconButton(
                onClick = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
                enabled = !operationsInProgress,
            ) {
                Icon(
                    Icons.Outlined.CloudSync,
                    stringResource(R.string.save_sync),
                )
            }
        }
        if (route.showBottomNavigation) {
            IconButton(
                onClick = { navController.navigate(MainRoute.SETTINGS.route) },
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    stringResource(R.string.settings),
                )
            }
        }
    }
}

@Composable
private fun LemuroidSearchView(
    mainUIState: MainViewModel.UiState,
    onUpdateQueryString: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Pixel style: the search field is a solid surfaceContainerHigh block,
    // not a floating tonal-elevation surface with a transparent field over
    // it (the old look read as glass).
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
    ) {
        TextField(
            value = mainUIState.searchQuery,
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(100),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            onValueChange = { onUpdateQueryString(it) },
            singleLine = true,
            keyboardActions =
                KeyboardActions(
                    onDone = { focusManager.clearFocus(true) },
                ),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
    }
}
