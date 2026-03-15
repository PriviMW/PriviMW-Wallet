package com.privimemobile.dapp

import android.util.Log

/**
 * Singleton that routes wallet API responses to the active DApp WebView.
 *
 * The C++ core sends ALL API responses through WalletListener.sendDAOApiResult().
 * When a DApp WebView is active, responses are routed based on call ID:
 *
 * - IDs >= 1,000,000 → PriviMe (React Native bridge) — these are PriviMe's own
 *   API calls that use a high ID range to avoid collision.
 * - String IDs starting with "ev_" → PriviMe event subscriptions → RN bridge.
 * - Everything else → DApp WebView (numeric IDs from 1..999999, string IDs
 *   like "call-1" from BeamDappConnector, etc.)
 *
 * This ensures PriviMe chat and DApps don't interfere with each other.
 */
object DAppResponseRouter {
    private val TAG = "DAppResponseRouter"
    private const val PRIVIME_ID_START = 1000000L

    // The currently active DApp WebView (only one at a time)
    private var activeWebView: BeamDAppWebView? = null

    /** Set the active DApp WebView that should receive API responses */
    fun setActiveWebView(webView: BeamDAppWebView?) {
        activeWebView = webView
        Log.d(TAG, "Active DApp WebView: ${if (webView != null) "set" else "cleared"}")
    }

    /**
     * Route an API response to the DApp WebView or let it fall through to RN bridge.
     * Returns true if consumed by DApp (caller should NOT forward to RN bridge).
     * Returns false if response belongs to PriviMe (caller should forward normally).
     *
     * Event responses (ev_system_state, ev_txs_changed) go to BOTH the DApp and PriviMe
     * because the DApp subscribes via ev_subunsub and needs these events to function,
     * while PriviMe also uses them for background message processing.
     */
    fun onApiResult(json: String): Boolean {
        val webView = activeWebView ?: return false

        if (isPriviMeResponse(json)) {
            // ev_* events: also forward to DApp WebView (DApp subscribed to these)
            if (isEventResponse(json)) {
                webView.dispatchApiResponse(json)
            }
            return false // Always let PriviMe handle its responses too
        }

        // Everything else goes to the DApp WebView only
        webView.dispatchApiResponse(json)
        return true
    }

    /** Check if this is an ev_* event notification */
    private fun isEventResponse(json: String): Boolean {
        val idIdx = json.indexOf("\"id\"")
        if (idIdx < 0) return false
        val colonIdx = json.indexOf(':', idIdx + 4)
        if (colonIdx < 0) return false
        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        return start + 4 < json.length &&
               json[start] == '"' &&
               json[start + 1] == 'e' && json[start + 2] == 'v' && json[start + 3] == '_'
    }

    /**
     * Check if a response belongs to PriviMe (not the DApp).
     * PriviMe uses call IDs >= 1,000,000 and event subscriptions use string IDs.
     */
    private fun isPriviMeResponse(json: String): Boolean {
        val idIdx = json.indexOf("\"id\"")
        if (idIdx < 0) return false

        val colonIdx = json.indexOf(':', idIdx + 4)
        if (colonIdx < 0) return false

        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length) return false

        // String IDs (e.g., "ev_system_state") → PriviMe event subscriptions
        if (json[start] == '"') {
            // Check for "ev_" prefix
            return start + 4 < json.length &&
                   json[start + 1] == 'e' && json[start + 2] == 'v' && json[start + 3] == '_'
        }

        // Numeric IDs >= 1,000,000 → PriviMe callbacks
        var end = start
        while (end < json.length && (json[end].isDigit() || json[end] == '-')) end++
        if (end == start) return false

        return try {
            json.substring(start, end).toLong() >= PRIVIME_ID_START
        } catch (e: NumberFormatException) {
            false
        }
    }

    /** Get the active DApp's display name (for consent dialog title) */
    fun getActiveAppName(): String? {
        return activeWebView?.let {
            try {
                val field = BeamDAppWebView::class.java.getDeclaredField("appName")
                field.isAccessible = true
                field.get(it) as? String
            } catch (_: Exception) { null }
        }
    }

    /** Clear all tracked state */
    fun clear() {
        activeWebView = null
    }
}
