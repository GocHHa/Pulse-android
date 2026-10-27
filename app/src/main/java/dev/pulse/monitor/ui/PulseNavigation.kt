package dev.pulse.monitor.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pulse.monitor.R
import dev.pulse.monitor.ui.battery.BatteryScreen
import dev.pulse.monitor.ui.components.PulseIcon
import dev.pulse.monitor.ui.components.rememberHaptics
import dev.pulse.monitor.ui.dashboard.DashboardScreen
import dev.pulse.monitor.ui.history.HistoryScreen
import dev.pulse.monitor.ui.memory.MemoryScreen
import dev.pulse.monitor.ui.network.NetworkScreen
import dev.pulse.monitor.ui.performance.PerformanceScreen
import dev.pulse.monitor.ui.settings.SettingsScreen
import dev.pulse.monitor.ui.theme.PulseColors
import dev.pulse.monitor.ui.theme.PulseTheme

object Routes {
    const val DASHBOARD = "dashboard"
    const val BATTERY = "battery"
    const val PERFORMANCE = "performance"
    const val MEMORY = "memory"
    const val NETWORK = "network"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class TopDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val icon: Int,
    val accent: (PulseColors) -> Color,
)

private val topDestinations = listOf(
    TopDestination(Routes.DASHBOARD, "Dashboard", R.drawable.ic_nav_dashboard) { it.battery },
    TopDestination(Routes.BATTERY, "Battery", R.drawable.ic_nav_battery) { it.battery },
    TopDestination(Routes.PERFORMANCE, "System", R.drawable.ic_nav_performance) { it.cpu },
    TopDestination(Routes.NETWORK, "Network", R.drawable.ic_nav_network) { it.network },
    TopDestination(Routes.HISTORY, "History", R.drawable.ic_nav_history) { it.battery },
)

@Composable
fun PulseRoot() {
    val navController = rememberNavController()
    val colors = PulseTheme.colors

    Scaffold(
        containerColor = colors.background,
        bottomBar = { PulseBottomBar(navController) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.985f) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        padding = innerPadding,
                        onOpen = { route -> navController.navigateTop(route) },
                        onOpenDetail = { route -> navController.navigate(route) },
                    )
                }
                composable(Routes.BATTERY) { BatteryScreen(innerPadding, onSettings = { navController.navigate(Routes.SETTINGS) }) }
                composable(Routes.PERFORMANCE) { PerformanceScreen(innerPadding, onSettings = { navController.navigate(Routes.SETTINGS) }) }
                composable(Routes.NETWORK) { NetworkScreen(innerPadding, onSettings = { navController.navigate(Routes.SETTINGS) }) }
                composable(Routes.HISTORY) { HistoryScreen(innerPadding, onSettings = { navController.navigate(Routes.SETTINGS) }) }
                composable(Routes.MEMORY) { MemoryScreen(innerPadding, onBack = { navController.popBackStack() }) }
                composable(Routes.SETTINGS) { SettingsScreen(innerPadding, onBack = { navController.popBackStack() }) }
            }

            // Soft scrim behind the transparent status bar so scrolled content stays legible.
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(colors.background, colors.background.copy(alpha = 0.0f)))),
            )
        }
    }
}

/** Navigate to a bottom-bar destination, keeping one copy of each tab and its scroll state. */
fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PulseBottomBar(navController: NavHostController) {
    val colors = PulseTheme.colors
    val haptics = rememberHaptics()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    NavigationBar(
        containerColor = colors.surface1,
        tonalElevation = 0.dp,
    ) {
        topDestinations.forEach { item ->
            val selected = destination?.hierarchy?.any { it.route == item.route } == true ||
                (item.route == Routes.DASHBOARD && destination?.route == Routes.MEMORY)
            val accent = item.accent(colors)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        haptics.tick()
                        navController.navigateTop(item.route)
                    }
                },
                icon = { PulseIcon(item.icon, contentDescription = null, tint = if (selected) accent else colors.textSecondary, size = 22.dp) },
                label = { Text(item.label, style = PulseTheme.type.caption) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = colors.textPrimary,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                    indicatorColor = accent.copy(alpha = 0.16f),
                ),
            )
        }
    }
}
