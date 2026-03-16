package com.privimemobile.ui.dapps

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.privimemobile.ui.theme.C
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.dapp.BeamDAppWebView
import com.privimemobile.dapp.DAppResponseRouter
import com.privimemobile.dapp.NativeTxApprovalDialog
import com.privimemobile.wallet.WalletManager

/**
 * Singleton that keeps the active DApp WebView alive across tab switches.
 * The WebView is detached from its parent when navigating away, and re-attached when returning.
 * Only destroyed when explicitly closed (back button) or a different DApp is launched.
 */
object DAppWebViewHolder {
    var activeWebView: BeamDAppWebView? = null
        private set
    var activeGuid: String = ""
        private set
    var activeName: String = ""
        private set

    fun getOrCreate(ctx: android.content.Context, name: String, path: String, guid: String): BeamDAppWebView {
        // If same DApp is already loaded, reuse it
        val existing = activeWebView
        if (existing != null && activeGuid == guid) {
            Log.d("DAppWebViewHolder", "Reusing existing WebView for '$name'")
            return existing
        }
        // Different DApp or none — destroy old, create new
        destroy()
        val wv = BeamDAppWebView(ctx).also {
            it.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            it.launchDApp(name, path)
        }
        activeWebView = wv
        activeGuid = guid
        activeName = name
        Log.d("DAppWebViewHolder", "Created new WebView for '$name' (guid=$guid)")
        return wv
    }

    fun destroy() {
        activeWebView?.let { wv ->
            wv.stopLoading()
            wv.removeJavascriptInterface("BEAM")
            DAppResponseRouter.setActiveWebView(null)
            // Detach from parent if still attached
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
        activeWebView = null
        activeGuid = ""
        activeName = ""
        // Restore PriviMe context
        val wallet = WalletManager.walletInstance
        if (wallet != null && Api.isWalletRunning()) {
            try { wallet.launchApp("PriviMe", "") } catch (_: Exception) {}
        }
    }

    /** Detach WebView from its current parent without destroying it. */
    fun detach() {
        activeWebView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
    }

    fun hasActive(): Boolean = activeWebView != null
}

/**
 * In-app DApp screen — hosts WebView within Compose navigation (tabs stay visible).
 * WebView survives tab switches via DAppWebViewHolder singleton.
 */
@Composable
fun DAppScreen(
    dappName: String,
    dappPath: String,
    dappGuid: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Set DApp activity ref for TX approval dialogs
    DisposableEffect(Unit) {
        NativeTxApprovalDialog.dappActivity = activity
        onDispose {
            NativeTxApprovalDialog.dappActivity = null
            // Detach but DON'T destroy — WebView stays alive for when user returns
            DAppWebViewHolder.detach()
        }
    }

    // Handle back press — this DESTROYS the DApp (user explicitly closes it)
    BackHandler {
        DAppWebViewHolder.destroy()
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with back button and DApp name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(C.card)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                DAppWebViewHolder.destroy()
                onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close DApp",
                    tint = C.text,
                )
            }
            Text(
                dappName,
                color = C.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // WebView fills remaining space
        AndroidView(
            factory = { ctx ->
                val wv = DAppWebViewHolder.getOrCreate(ctx, dappName, dappPath, dappGuid)
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
