package com.privimemobile.dapp

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.wallet.WalletManager

/**
 * Standalone Activity for running DApps — completely isolated from React Native.
 *
 * Why a separate Activity:
 * - Heavy DApp WebView JS (AMM/DEX) starves the RN JS thread for 15-20s,
 *   freezing tabs, navigation, and event delivery across the entire app.
 * - A separate Activity gives the WebView its own view hierarchy.
 * - Back button = Activity.finish() — instant, no RN navigation needed.
 * - TX consent uses NativeTxApprovalDialog — pure Android, no RN bridge.
 *
 * Intent extras:
 *   "dapp_name"  — display name (e.g., "BEAM All-in-One")
 *   "dapp_path"  — file:// URL to index.html
 *   "dapp_guid"  — DApp GUID for tracking
 */
class DAppActivity : AppCompatActivity() {

    private val TAG = "DAppActivity"
    private var webView: BeamDAppWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = intent.getStringExtra("dapp_name") ?: "DApp"
        val path = intent.getStringExtra("dapp_path") ?: ""
        val guid = intent.getStringExtra("dapp_guid") ?: ""

        if (path.isEmpty()) {
            Log.e(TAG, "No dapp_path — finishing")
            finish()
            return
        }

        // Full-screen dark container
        val container = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#042548"))
        }
        setContentView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Create and add the WebView
        webView = BeamDAppWebView(this).also { wv ->
            container.addView(wv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            wv.launchDApp(name, path)
        }

        Log.d(TAG, "Launched DApp '$name' (guid=$guid)")
    }

    override fun onResume() {
        super.onResume()
        NativeTxApprovalDialog.dappActivity = this
    }

    override fun onPause() {
        NativeTxApprovalDialog.dappActivity = null
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Immediately stop the WebView and restore PriviMe context.
        // Do NOT call destroy() here — it blocks the UI thread for seconds
        // on heavy DApps (AMM/DEX with React+webpack).
        webView?.let {
            it.stopLoading()
            it.removeJavascriptInterface("BEAM")
            DAppResponseRouter.setActiveWebView(null)
        }
        restorePriviMeContext()
        finish()
    }

    override fun onDestroy() {
        // Do NOT call webView.destroy() — it blocks the shared UI thread for 20+ seconds
        // on heavy DApps (AMM React+webpack). The UI thread is shared across all Activities
        // in the same process, so this freezes the main RN Activity too.
        // loadUrl("about:blank") in onBackPressed already released JS/DOM state.
        // GC will clean up the native WebView resources asynchronously.
        webView = null
        super.onDestroy()
    }

    private fun restorePriviMeContext() {
        val wallet = WalletManager.walletInstance
        if (wallet != null && Api.isWalletRunning()) {
            try {
                wallet.launchApp("PriviMe", "")
                Log.d(TAG, "Restored PriviMe API context")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore PriviMe context: ${e.message}")
            }
        }
    }
}
