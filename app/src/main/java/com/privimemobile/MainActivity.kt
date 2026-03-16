package com.privimemobile

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
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
                            android.os.Handler(mainLooper).postDelayed({ startProtocol() }, 2000)
                        },
                    )
                    else -> AppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WalletManager.currentActivity = this
        // Foreground recovery (matches RN useProtocol foreground handler)
        val wallet = WalletManager.walletInstance
        if (wallet != null) {
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
                ProtocolStartup.onForegroundRecovery()
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
        WalletApi.subscribeToEvents()
        ProtocolStartup.init(this, lifecycleScope)
        // Auto-start background service (matches RN useBackgroundService hook)
        startBackgroundServiceIfEnabled()
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
        ProtocolStartup.shutdown()
        WalletApi.stop()
        super.onDestroy()
    }
}
