package com.lumitalk.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lumitalk.ui.screens.MainScreen
import com.lumitalk.ui.screens.SendScreen
import com.lumitalk.ui.screens.ReceiveScreen
import com.lumitalk.ui.screens.PlaceholderScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(BottomNavItem.Home.route) {
            MainScreen()
        }
            composable(BottomNavItem.Send.route) {
            SendScreen()
        }
        composable(BottomNavItem.Receive.route) {
            ReceiveScreen()
        }
        composable(BottomNavItem.Settings.route) {
            PlaceholderScreen()
        }
    }
}
