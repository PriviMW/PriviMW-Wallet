package com.privimemobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.auth.LockScreen
import com.privimemobile.ui.auth.OnboardingScreen
import com.privimemobile.ui.theme.C
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
                        },
                    )
                    !unlocked -> LockScreen(
                        onUnlocked = { unlocked = true },
                    )
                    else -> MainApp()
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
}

@Composable
private fun MainApp() {
    // Placeholder — will be replaced with bottom tab navigation
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PriviMW", color = C.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Wallet unlocked", color = C.textSecondary)
        }
    }
}
