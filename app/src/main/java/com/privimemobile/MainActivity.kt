package com.privimemobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
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
                            WalletApi.start(lifecycleScope)
                            WalletApi.subscribeToEvents()
                        },
                    )
                    !unlocked -> LockScreen(
                        onUnlocked = {
                            unlocked = true
                            WalletApi.start(lifecycleScope)
                            WalletApi.subscribeToEvents()
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
    }

    override fun onPause() {
        if (WalletManager.currentActivity === this) {
            WalletManager.currentActivity = null
        }
        super.onPause()
    }

    override fun onDestroy() {
        WalletApi.stop()
        super.onDestroy()
    }
}
