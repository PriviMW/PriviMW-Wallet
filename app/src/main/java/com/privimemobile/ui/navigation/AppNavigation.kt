package com.privimemobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.privimemobile.ui.theme.C
import com.privimemobile.ui.wallet.WalletScreen
import com.privimemobile.ui.wallet.SendScreen
import com.privimemobile.ui.wallet.ReceiveScreen
import com.privimemobile.ui.chat.ChatsScreen
import com.privimemobile.ui.dapps.DAppsScreen
import com.privimemobile.ui.settings.SettingsScreen

/**
 * Bottom navigation tabs for the main app.
 */
enum class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    WALLET("wallet", "Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    CHATS("chats", "Chats", Icons.Filled.Chat, Icons.Outlined.Chat),
    DAPPS("dapps", "DApps", Icons.Filled.Apps, Icons.Outlined.Apps),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = C.bg,
        bottomBar = {
            NavigationBar(
                containerColor = C.card,
                contentColor = C.text,
            ) {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.accent,
                            selectedTextColor = C.accent,
                            unselectedIconColor = C.textSecondary,
                            unselectedTextColor = C.textSecondary,
                            indicatorColor = C.cardAlt,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.WALLET.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.WALLET.route) {
                WalletScreen(
                    onSend = { navController.navigate("send") },
                    onReceive = { navController.navigate("receive") },
                )
            }
            composable("send") {
                SendScreen(
                    onBack = { navController.popBackStack() },
                    onSent = { navController.popBackStack() },
                )
            }
            composable("receive") {
                ReceiveScreen(onBack = { navController.popBackStack() })
            }
            composable(Tab.CHATS.route) { ChatsScreen() }
            composable(Tab.DAPPS.route) { DAppsScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen() }
        }
    }
}
