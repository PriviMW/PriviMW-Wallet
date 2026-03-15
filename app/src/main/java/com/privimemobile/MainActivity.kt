package com.privimemobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.privimemobile.wallet.WalletManager

class MainActivity : ComponentActivity() {
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
        // Foreground recovery: refresh wallet state and re-subscribe to events
        val wallet = WalletManager.walletInstance
        if (wallet != null) {
            try {
                wallet.getWalletStatus()
                wallet.getTransactions()
                wallet.getAddresses(true)
            } catch (_: Exception) {}
            WalletApi.resubscribeEvents()
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
    }

    override fun onDestroy() {
        ProtocolStartup.shutdown()
        WalletApi.stop()
        super.onDestroy()
    }
}
