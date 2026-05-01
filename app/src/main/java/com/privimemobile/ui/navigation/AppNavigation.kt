package com.privimemobile.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.privimemobile.MainActivity
import androidx.annotation.StringRes
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
import com.privimemobile.R
import com.privimemobile.ui.theme.C
import com.privimemobile.ui.wallet.WalletScreen
import com.privimemobile.ui.wallet.SendScreen
import com.privimemobile.ui.wallet.ReceiveScreen
import com.privimemobile.ui.wallet.SendConfirmScreen
import com.privimemobile.ui.wallet.TransactionDetailScreen
import com.privimemobile.ui.wallet.TransactionHistoryScreen
import com.privimemobile.ui.wallet.UTXOScreen
import com.privimemobile.ui.wallet.AddressesScreen
import com.privimemobile.ui.wallet.AssetDetailScreen
import com.privimemobile.ui.wallet.QRScannerScreen
import com.privimemobile.ui.dapps.DAppScreen
import com.privimemobile.ui.dapps.DAppStoreBrowseScreen
import com.privimemobile.ui.dapps.PublishersScreen
import com.privimemobile.ui.chat.ChatScreen
import com.privimemobile.ui.chat.ChatsScreen
import com.privimemobile.ui.chat.ContactInfoScreen
import com.privimemobile.ui.chat.CreateGroupScreen
import com.privimemobile.ui.chat.GroupSettingsScreen
import com.privimemobile.ui.chat.MediaGalleryScreen
import com.privimemobile.ui.chat.NewChatScreen
import com.privimemobile.ui.chat.RegisterScreen
import com.privimemobile.ui.chat.SearchScreen
import com.privimemobile.ui.dapps.DAppsScreen
import com.privimemobile.ui.settings.SettingsScreen

/**
 * Bottom navigation tabs for the main app.
 */
enum class Tab(
    val route: String,
    @StringRes val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    WALLET("wallet", R.string.wallet_title, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    CHATS("chats", R.string.chat_title, Icons.Filled.Chat, Icons.Outlined.Chat),
    DAPPS("dapps", R.string.dapps_title, Icons.Filled.Apps, Icons.Outlined.Apps),
    SWAP("swap_tab", R.string.swap_title, Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    SETTINGS("settings", R.string.settings_title, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun AppNavigation() {
    // 1-pixel keepalive: tricks LTPO displays into staying at 120Hz by continuously
    // animating an invisible pixel. The alpha oscillates between 0.001 and 0.002 every
    // 16ms (60fps minimum frame request). LTPO controller sees continuous invalidation → 120Hz.
    val fpsTransition = rememberInfiniteTransition(label = "fps-keepalive")
    val fpsAlpha by fpsTransition.animateFloat(
        initialValue = 0.001f, targetValue = 0.002f,
        animationSpec = infiniteRepeatable(tween(16, easing = LinearEasing), RepeatMode.Reverse),
        label = "fps-alpha",
    )
    Box(Modifier.size(1.dp).alpha(fpsAlpha))

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Deep-link: navigate to chat when notification is tapped
    val activity = LocalContext.current as? MainActivity
    val pendingDeepLink by activity?.pendingDeepLink?.collectAsState() ?: remember { mutableStateOf(null) }
    LaunchedEffect(pendingDeepLink) {
        val convKey = pendingDeepLink ?: return@LaunchedEffect
        if (convKey.startsWith("g_")) {
            // Group chat — convKey is "g_{groupId.take(16)}", need full groupId from DB
            val groupIdPrefix = convKey.removePrefix("g_")
            val groupEntity = com.privimemobile.chat.ChatService.db?.groupDao()?.findByConvKey(groupIdPrefix)
            val groupId = groupEntity?.groupId ?: convKey.removePrefix("g_")
            navController.navigate("group_chat/$groupId") {
                popUpTo(Tab.CHATS.route) { inclusive = false }
                launchSingleTop = true
            }
        } else {
            // DM chat — convKey is "@handle"
            val handle = convKey.removePrefix("@")
            if (handle.isNotEmpty()) {
                navController.navigate("chat/$handle") {
                    popUpTo(Tab.CHATS.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
        activity?.consumeDeepLink()
    }

    // Hide bottom bar on certain screens (chat screens only — wallet sub-screens keep nav bar)
    val hideBottomBar = currentDestination?.route?.let { route ->
        route.startsWith("chat/") ||
                route == "new_chat" ||
                route == "search_messages" ||
                route.startsWith("media_gallery/") ||
                route.startsWith("contact_info/") ||
                route.startsWith("group_chat/") ||
                route.startsWith("group_settings/") ||
                route == "create_group"
    } ?: false

    Scaffold(
        containerColor = C.bg,
        bottomBar = {
            if (!hideBottomBar) {
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
                                    contentDescription = stringResource(tab.labelResId),
                                )
                            },
                            label = { Text(stringResource(tab.labelResId)) },
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
            }
        },
    ) { innerPadding ->
        Box(Modifier.size(1.dp).alpha(fpsAlpha)) // keepalive inside Scaffold render tree
        NavHost(
            navController = navController,
            startDestination = Tab.WALLET.route,
            modifier = Modifier.padding(innerPadding),
            // Telegram-style: 400ms forward, 500ms back, cubic ease-out
            enterTransition = {
                slideInHorizontally(tween(400, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) { it } +
                    fadeIn(tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
            },
            exitTransition = {
                slideOutHorizontally(tween(400, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) { -it / 3 } +
                    fadeOut(tween(250))
            },
            popEnterTransition = {
                slideInHorizontally(tween(500, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))) { -it / 3 } +
                    fadeIn(tween(350))
            },
            popExitTransition = {
                slideOutHorizontally(tween(500, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f))) { it } +
                    fadeOut(tween(300))
            },
        ) {
            // Tab screens: instant fade (no slide — lateral navigation)
            composable(
                Tab.WALLET.route,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                WalletScreen(
                    onSend = { navController.navigate("send") },
                    onReceive = { navController.navigate("receive") },
                    onTxDetail = { txId -> navController.navigate("tx_detail/$txId") },
                    onAssetDetail = { assetId -> navController.navigate("asset_detail/$assetId") },
                    onTxHistory = { navController.navigate("tx_history") },
                )
            }
            composable(
                "send?assetId={assetId}",
                arguments = listOf(
                    navArgument("assetId") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { backStackEntry ->
                // Read QR result passed back via savedStateHandle
                val scannedAddress = backStackEntry.savedStateHandle
                    ?.getStateFlow<String?>("scanned_address", null)
                    ?.collectAsState()?.value
                val assetId = backStackEntry.arguments?.getInt("assetId") ?: 0
                SendScreen(
                    onBack = { navController.popBackStack() },
                    onSent = { navController.popBackStack() },
                    onScanQr = { navController.navigate("qr_scanner") },
                    scannedAddress = scannedAddress,
                    initialAssetId = assetId,
                    onNavigateConfirm = { address, amount, fee, comment, asset, txType ->
                        navController.navigate(
                            "send_confirm/$address/$amount/$fee/${java.net.URLEncoder.encode(comment, "UTF-8")}/$asset/$txType"
                        )
                    },
                )
            }
            composable(
                "send_confirm/{address}/{amount}/{fee}/{comment}/{assetId}/{txType}",
                arguments = listOf(
                    navArgument("address") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.LongType },
                    navArgument("fee") { type = NavType.LongType },
                    navArgument("comment") { type = NavType.StringType; defaultValue = "" },
                    navArgument("assetId") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("txType") { type = NavType.StringType; defaultValue = "offline" },
                ),
            ) { backStackEntry ->
                val address = backStackEntry.arguments?.getString("address") ?: ""
                val amount = backStackEntry.arguments?.getLong("amount") ?: 0L
                val fee = backStackEntry.arguments?.getLong("fee") ?: 0L
                val comment = try {
                    java.net.URLDecoder.decode(
                        backStackEntry.arguments?.getString("comment") ?: "", "UTF-8"
                    )
                } catch (_: Exception) { "" }
                val assetId = backStackEntry.arguments?.getInt("assetId") ?: 0
                val txType = backStackEntry.arguments?.getString("txType") ?: "offline"

                SendConfirmScreen(
                    address = address,
                    amountGroth = amount,
                    fee = fee,
                    comment = comment,
                    assetId = assetId,
                    txType = txType,
                    onApproved = {
                        // Pop back to wallet home
                        navController.popBackStack(Tab.WALLET.route, inclusive = false)
                    },
                    onRejected = { navController.popBackStack() },
                )
            }
            composable("receive") {
                ReceiveScreen(onBack = { navController.popBackStack() })
            }
            composable("tx_detail/{txId}") { backStackEntry ->
                val txId = backStackEntry.arguments?.getString("txId") ?: ""
                TransactionDetailScreen(txId = txId, onBack = { navController.popBackStack() })
            }
            composable("tx_history") {
                TransactionHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onTxDetail = { txId -> navController.navigate("tx_detail/$txId") },
                )
            }
            composable(
                "asset_detail/{assetId}",
                arguments = listOf(navArgument("assetId") { type = NavType.IntType; defaultValue = 0 }),
            ) { backStackEntry ->
                val assetId = backStackEntry.arguments?.getInt("assetId") ?: 0
                AssetDetailScreen(
                    assetId = assetId,
                    onBack = { navController.popBackStack() },
                    onSend = { navController.navigate("send?assetId=$assetId") },
                    onReceive = { navController.navigate("receive") },
                    onTxDetail = { txId -> navController.navigate("tx_detail/$txId") },
                )
            }
            composable("utxos") {
                UTXOScreen(onBack = { navController.popBackStack() })
            }
            composable("addresses") {
                AddressesScreen(onBack = { navController.popBackStack() })
            }
            composable("swap") {
                com.privimemobile.ui.wallet.SwapScreen(
                    onBack = { navController.popBackStack() },
                    onCreateOffer = { navController.navigate("create_offer") },
                    onTxDetail = { txId -> navController.navigate("tx_detail/$txId") },
                )
            }
            composable("create_offer") {
                com.privimemobile.ui.wallet.CreateOfferScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable("qr_scanner") {
                QRScannerScreen(
                    onScanned = { address ->
                        // Pass result back to the existing Send screen via savedStateHandle
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set("scanned_address", address)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Tab.CHATS.route,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                ChatsScreen(
                    onOpenChat = { handle -> navController.navigate("chat/$handle") },
                    onNewChat = { navController.navigate("new_chat") },
                    onRegister = { navController.navigate("register") },
                    onSearch = { navController.navigate("search_messages") },
                    onCreateGroup = { navController.navigate("create_group") },
                    onOpenGroup = { groupId -> navController.navigate("group_chat/$groupId") },
                )
            }
            composable(
                "chat/{handle}?scrollToTs={scrollToTs}",
                arguments = listOf(
                    navArgument("handle") { type = NavType.StringType },
                    navArgument("scrollToTs") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { backStackEntry ->
                val handle = backStackEntry.arguments?.getString("handle") ?: ""
                val scrollToTs = backStackEntry.arguments?.getLong("scrollToTs") ?: 0L
                ChatScreen(
                    handle = handle,
                    onBack = { navController.popBackStack() },
                    onMediaGallery = { navController.navigate("media_gallery/$handle") },
                    onContactInfo = { navController.navigate("contact_info/$handle") },
                    onNavigateToChat = { toHandle ->
                        if (toHandle.startsWith("group:")) {
                            val gid = toHandle.removePrefix("group:")
                            navController.popBackStack("chats", false)
                            navController.navigate("group_chat/$gid")
                        } else {
                            navController.popBackStack("chats", false)
                            navController.navigate("chat/$toHandle")
                        }
                    },
                    scrollToTimestamp = scrollToTs,
                )
            }
            composable("new_chat") {
                NewChatScreen(
                    onStartChat = { handle ->
                        navController.popBackStack()
                        navController.navigate("chat/$handle")
                    },
                    onBack = { navController.popBackStack() },
                    onJoinGroup = { groupId ->
                        navController.popBackStack()
                        navController.navigate("group_chat/$groupId")
                    },
                )
            }
            composable("register") {
                RegisterScreen(
                    onRegistered = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("search_messages") {
                SearchScreen(
                    onOpenChat = { handle, scrollToTs ->
                        navController.popBackStack()
                        navController.navigate("chat/$handle?scrollToTs=$scrollToTs")
                    },
                    onOpenGroupChat = { groupId ->
                        navController.popBackStack()
                        navController.navigate("group_chat/$groupId")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("media_gallery/{handle}") { backStackEntry ->
                val handle = backStackEntry.arguments?.getString("handle") ?: ""
                MediaGalleryScreen(
                    handle = handle,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("contact_info/{handle}") { backStackEntry ->
                val handle = backStackEntry.arguments?.getString("handle") ?: ""
                ContactInfoScreen(
                    handle = handle,
                    onBack = { navController.popBackStack() },
                    onMediaGallery = {
                        navController.navigate("media_gallery/$handle")
                    },
                    onDeleteChat = {
                        // Pop back to chats list
                        navController.popBackStack("chats", inclusive = false)
                    },
                )
            }
            composable("create_group") {
                CreateGroupScreen(
                    onBack = { navController.popBackStack() },
                    onGroupCreated = { navController.popBackStack() },
                )
            }
            composable(
                "group_chat/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                ChatScreen(
                    handle = "",
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onMediaGallery = { navController.navigate("media_gallery/$groupId") },
                    onGroupSettings = { navController.navigate("group_settings/$groupId") },
                    onViewContact = { h -> navController.navigate("contact_info/$h") },
                    onNavigateToChat = { toHandle ->
                        if (toHandle.startsWith("group:")) {
                            val gid = toHandle.removePrefix("group:")
                            navController.popBackStack("chats", false)
                            navController.navigate("group_chat/$gid")
                        } else {
                            navController.popBackStack("chats", false)
                            navController.navigate("chat/$toHandle")
                        }
                    },
                )
            }
            composable(
                "group_settings/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                GroupSettingsScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onDeleteGroup = {
                        navController.popBackStack(Tab.CHATS.route, inclusive = false)
                    },
                    onContactInfo = { handle ->
                        navController.navigate("contact_info/$handle")
                    },
                )
            }
            composable(
                Tab.DAPPS.route,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                DAppsScreen(
                    onBrowseStore = { navController.navigate("dapp_store") },
                    onLaunchDApp = { name, path, guid ->
                        navController.navigate(
                            "dapp_view/${java.net.URLEncoder.encode(name, "UTF-8")}/${java.net.URLEncoder.encode(path, "UTF-8")}/$guid"
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                "dapp_view/{name}/{path}/{guid}",
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType },
                    navArgument("guid") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val name = try { java.net.URLDecoder.decode(backStackEntry.arguments?.getString("name") ?: "", "UTF-8") } catch (_: Exception) { "DApp" }
                val path = try { java.net.URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", "UTF-8") } catch (_: Exception) { "" }
                val guid = backStackEntry.arguments?.getString("guid") ?: ""
                DAppScreen(
                    dappName = name,
                    dappPath = path,
                    dappGuid = guid,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("dapp_store") {
                DAppStoreBrowseScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPublishers = { navController.navigate("dapp_publishers") },
                )
            }
            composable("dapp_publishers") {
                PublishersScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Tab.SWAP.route,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                com.privimemobile.ui.wallet.SwapScreen(
                    onBack = { navController.popBackStack() },
                    onCreateOffer = { navController.navigate("create_offer") },
                    onTxDetail = { txId -> navController.navigate("tx_detail/$txId") },
                )
            }
            composable(
                Tab.SETTINGS.route,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                SettingsScreen(
                    onNavigateAddresses = { navController.navigate("addresses") },
                    onNavigateUtxo = { navController.navigate("utxos") },
                )
            }
        }
    }
}
