package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

@Composable
fun MainNavigationBar(
    currentRoute: MainRoute?,
    navController: NavHostController,
) {
    AnimatedVisibility(
        visible = currentRoute?.showBottomNavigation != false,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        LemuroidNavigationBar(currentRoute, navController)
    }
}

/**
 * Standard M3 bottom navigation (Pixel style): docked to the bottom edge on
 * a solid surfaceContainer block, with the built-in pill indicator behind
 * the selected item's icon. This replaces the old floating capsule bar
 * (detached rounded bar, drop shadow, red-to-purple gradient pill), which
 * read as glassy/iOS-like next to the Pixel tonal surfaces.
 */
@Composable
private fun LemuroidNavigationBar(
    currentRoute: MainRoute?,
    navController: NavHostController,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        MainNavigationRoutes.values().forEach { destination ->
            val isSelected = currentRoute?.root == destination.route
            val iconDrawable = if (isSelected) destination.selectedIcon else destination.unselectedIcon
            val title = stringResource(destination.titleId)

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(destination.route.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = false
                    }
                },
                icon = {
                    Icon(
                        imageVector = iconDrawable,
                        contentDescription = title,
                    )
                },
                label = { Text(text = title) },
            )
        }
    }
}
