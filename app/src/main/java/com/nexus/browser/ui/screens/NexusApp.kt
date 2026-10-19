package com.nexus.browser.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nexus.browser.data.SettingsRepository

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Browser : Screen("browser")
    data object Player : Screen("player")
    data object Settings : Screen("settings")
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NexusApp(settings: SettingsRepository) {
    val navController = rememberNavController()
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var videoUrl by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onSearch = { query ->
                    currentUrl = if (query.startsWith("http")) query else "https://www.google.com/search?q=$query"
                    navController.navigate(Screen.Browser.route)
                },
                onOpenUrl = { url ->
                    currentUrl = url
                    navController.navigate(Screen.Browser.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Browser.route) {
            BrowserScreen(
                initialUrl = currentUrl,
                onBack = { navController.popBackStack() },
                onPlayVideo = { url ->
                    videoUrl = url
                    navController.navigate(Screen.Player.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Player.route) {
            videoUrl?.let { url ->
                VideoPlayerScreen(
                    videoUrl = url,
                    onBack = {
                        videoUrl = null
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
