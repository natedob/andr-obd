package com.natan.obd.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.natan.obd.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natan.obd.ui.diagnostics.DiagnosticsScreen
import com.natan.obd.ui.drive.DriveScreen
import com.natan.obd.ui.settings.SettingsViewModel
import com.natan.obd.ui.history.HistoryScreen
import com.natan.obd.ui.history.TripDetailScreen
import com.natan.obd.ui.settings.SettingsScreen

private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    Drive("drive", R.string.nav_drive, Icons.Default.Speed),
    History("history", R.string.nav_history, Icons.Default.History),
    Diagnostics("diagnostics", R.string.nav_diagnostics, Icons.Default.Build),
    Settings("settings", R.string.nav_settings, Icons.Default.Settings),
}

private const val TRIP_ROUTE = "trip/{id}"

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val settings by hiltViewModel<SettingsViewModel>().settings.collectAsStateWithLifecycle()
    val units = Units(imperial = settings?.useImperial == true)
    val snackbar = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    CompositionLocalProvider(LocalUnits provides units) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Tab.Drive.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Drive.route) {
                DriveScreen(snackbar, onTripSaved = { id -> navController.navigate("trip/$id") })
            }
            composable(Tab.History.route) { HistoryScreen(onOpenTrip = { navController.navigate("trip/$it") }) }
            composable(Tab.Diagnostics.route) { DiagnosticsScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(TRIP_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                TripDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
    }
}
