package com.privimemobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C
import androidx.lifecycle.lifecycleScope
import com.privimemobile.protocol.LocaleHelper
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.auth.LockScreen
import com.privimemobile.ui.auth.OnboardingScreen
import com.privimemobile.ui.navigation.AppNavigation
import com.privimemobile.ui.theme.PriviMWTheme
import com.privimemobile.wallet.BackgroundService
import com.privimemobile.wallet.UpdateChecker
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : FragmentActivity() {

    /** Deep-link target: convKey to open (set by notification tap). */
    private val _pendingDeepLink = MutableStateFlow<String?>(null)
    val pendingDeepLink: StateFlow<String?> = _pendingDeepLink.asStateFlow()

    fun consumeDeepLink() { _pendingDeepLink.value = null }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Check if launched from notification
        handleDeepLink(intent)

        WalletManager.init(this)
        com.privimemobile.ui.theme.C.loadSavedTheme(this)
        // Let system handle nav bar color (follows system theme)

        // Force max refresh rate (120Hz)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
            if (disp != null) {
                val currentMode = disp.mode
                val bestMode = disp.supportedModes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = bestMode.modeId
                        preferredRefreshRate = bestMode.refreshRate
                    }
                }
            }
        }

        // API 35+ (Android 15): Disable LTPO power-saving frame rate drops
        try {
            val cls = window.javaClass
            // isFrameRatePowerSavingsBalanced = false → prevents LTPO from dropping refresh rate
            val method = cls.getMethod("setFrameRatePowerSavingsBalanced", Boolean::class.java)
            method.invoke(window, false)
            Log.d("MainActivity", "Disabled frame rate power savings (LTPO locked to max)")
        } catch (e: Exception) {
            Log.d("MainActivity", "Frame rate power savings API not available: ${e.message}")
        }

        // API 35+: Set requested frame rate on the root view
        try {
            window.decorView.post {
                try {
                    val method = android.view.View::class.java.getMethod("setRequestedFrameRate", Float::class.java)
                    method.invoke(window.decorView, 120f)
                    Log.d("MainActivity", "Set decorView requestedFrameRate=120")
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Request notification permission (Android 13+) — needed for badge count
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        setContent {
            PriviMWTheme {
                // Update status bar icons based on theme (dark icons for light themes)
                val isLight = com.privimemobile.ui.theme.C.isLight
                androidx.compose.runtime.SideEffect {
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = isLight
                    controller.isAppearanceLightNavigationBars = isLight
                }
                Box(modifier = Modifier.fillMaxSize().imePadding()) {
                var hasWallet by remember { mutableStateOf(WalletManager.isWalletCreated()) }
                var unlocked by remember { mutableStateOf(false) }

                when {
                    !hasWallet -> OnboardingScreen(
                        onWalletReady = {
                            hasWallet = true
                            unlocked = true
                            // Delay protocol start — launchApp("PriviMe") is async,
                            // the API context needs time to initialize before callWalletApi.
                            android.os.Handler(mainLooper).postDelayed({ startProtocol() }, 2000)
                        },
                    )
                    !unlocked -> LockScreen(
                        onUnlocked = {
                            unlocked = true
                            if (WalletManager.isApiReady) {
                                // Wallet already open (BackgroundService kept it alive).
                                // Re-start WalletApi with new Activity's lifecycleScope
                                // (old scope was destroyed with previous Activity).
                                WalletApi.start(lifecycleScope)
                                WalletApi.subscribeToEvents()
                                ProtocolStartup.onForegroundRecovery(lifecycleScope)
                                // Ensure ChatService is running (may not have been init'd on reuse path)
                                com.privimemobile.chat.ChatService.init(this@MainActivity)
                                com.privimemobile.chat.ChatService.onForegroundRecovery()
                            } else {
                                android.os.Handler(mainLooper).postDelayed({ startProtocol() }, 2000)
                            }
                        },
                    )
                    else -> {
                        AppNavigation()
                        // Update check on app open
                        var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(3000) // Wait for UI to settle after unlock
                            updateInfo = UpdateChecker.checkForUpdate(this@MainActivity)
                        }
                        if (updateInfo != null) {
                            UpdateDialog(
                                info = updateInfo!!,
                                onDismiss = { updateInfo = null },
                                onViewRelease = {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateInfo!!.releaseUrl))
                                    startActivity(intent)
                                    updateInfo = null
                                }
                            )
                        }
                    }
                }
                } // Box imePadding
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val convKey = intent?.getStringExtra("open_chat")
        if (convKey != null) {
            Log.d("MainActivity", "Deep-link to chat: $convKey")
            _pendingDeepLink.value = convKey
            // Clear the extra so it doesn't re-trigger
            intent.removeExtra("open_chat")
        }
    }

    override fun onResume() {
        super.onResume()
        WalletManager.currentActivity = this
        // Foreground recovery (matches RN useProtocol foreground handler)
        val wallet = WalletManager.walletInstance
        if (wallet != null) {
            // Re-start WalletApi with current Activity's lifecycleScope
            // (previous scope may have been destroyed if Activity was recreated)
            WalletApi.start(lifecycleScope)
            // DO NOT call cleanupStaleCallbacks() here. A password manager overlay
            // or system dialog can trigger onPause/onResume while a TX (e.g. /tip)
            // is pending. Clearing callbacks would drop the active response handler,
            // causing callAsync to hang forever and the chat message to never send.
            // Callbacks are already cleared in WalletApi.stop() on wallet shutdown.
            // Re-subscribe to wallet events (C++ core may have dropped them)
            WalletApi.resubscribeEvents()
            // Refresh wallet state
            try {
                wallet.getWalletStatus()
                wallet.getTransactions()
                wallet.getAddresses(true)
            } catch (_: Exception) {}
            // Restart protocol polling & refresh after C++ core wakes up
            android.os.Handler(mainLooper).postDelayed({
                ProtocolStartup.onForegroundRecovery(lifecycleScope)
                com.privimemobile.chat.ChatService.onForegroundRecovery()
            }, 1500)
        }
    }

    override fun onPause() {
        if (WalletManager.currentActivity === this) {
            WalletManager.currentActivity = null
        }
        super.onPause()
    }

    private fun startProtocol() {
        WalletApi.start(lifecycleScope)
        // Wait for API context to be ready before subscribing — callWalletApi crashes if
        // AppsApiUI is null (launchApp is async). Retry every 500ms up to 15s.
        val handler = android.os.Handler(mainLooper)
        var attempts = 0
        fun trySubscribe() {
            if (WalletManager.isApiReady || attempts >= 30) {
                WalletApi.subscribeToEvents()
                ProtocolStartup.init(this@MainActivity, lifecycleScope)
                // Initialize the new Room-based chat system
                com.privimemobile.chat.ChatService.init(this@MainActivity)
                startBackgroundServiceIfEnabled()
            } else {
                attempts++
                handler.postDelayed(::trySubscribe, 500)
            }
        }
        trySubscribe()
    }

    private fun startBackgroundServiceIfEnabled() {
        val enabled = SecureStorage.getBoolean("bg_service_enabled", true) // default ON like RN
        if (!enabled) return
        try {
            val intent = Intent(this, BackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d("MainActivity", "Background service auto-started")
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to start background service: ${e.message}")
        }
    }

    override fun onDestroy() {
        if (!BackgroundService.isRunning) {
            // No background service — full shutdown
            ProtocolStartup.shutdown()
            WalletApi.stop()
            WalletManager.closeWallet()
        }
        // If BackgroundService is running, keep wallet alive for TXs/messages
        super.onDestroy()
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onViewRelease: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = {
            Text("Update Available", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Version ${info.latestVersion} is available", color = C.text, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Current: ${info.currentVersion}", color = C.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onViewRelease,
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
            ) {
                Text("View Release", color = C.bg)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = C.textSecondary)
            }
        },
    )
}
