package com.lumitalk.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem("home", "Home", Icons.Filled.Home)
    object Send : BottomNavItem("send", "Send", Icons.Filled.FlashOn)
    object Receive : BottomNavItem("receive", "Receive", Icons.Filled.CameraAlt)
    object Settings : BottomNavItem("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Send,
    BottomNavItem.Receive,
    BottomNavItem.Settings
)
