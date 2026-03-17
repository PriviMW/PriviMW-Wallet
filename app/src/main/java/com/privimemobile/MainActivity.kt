package com.privimemobile

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.auth.LockScreen
import com.privimemobile.ui.auth.OnboardingScreen
import com.privimemobile.ui.navigation.AppNavigation
import com.privimemobile.ui.theme.PriviMWTheme
import com.privimemobile.wallet.BackgroundService
import com.privimemobile.wallet.WalletManager

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WalletManager.init(this)
        window.navigationBarColor = Color.parseColor("#0a0e27")

        setContent {
            PriviMWTheme {
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
                            } else {
                                android.os.Handler(mainLooper).postDelayed({ startProtocol() }, 2000)
                            }
                        },
                    )
                    else -> AppNavigation()
                }
                } // Box imePadding
            }
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
            // Clear stale callbacks that will never get responses
            WalletApi.cleanupStaleCallbacks()
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
