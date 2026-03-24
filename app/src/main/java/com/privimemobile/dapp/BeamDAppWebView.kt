package com.privimemobile.dapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.wallet.WalletManager
import java.io.File

/**
 * Native Android WebView with Beam wallet API bridge via addJavascriptInterface.
 *
 * This matches the official Beam Android wallet's architecture exactly:
 * - window.BEAM is a native Java object (not a JS shim)
 * - callWalletApi(json) goes directly to JNI (no postMessage round-trip)
 * - Responses dispatched via evaluateJavascript + CustomEvent("onCallWalletApiResult")
 *
 * The official wallet uses:
 *   webView.addJavascriptInterface(webInterface, "BEAM")
 *   where webInterface implements IWebInterface with @JavascriptInterface callWalletApi
 *
 * We do the same thing here.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class BeamDAppWebView(context: Context) : WebView(context) {

    private val TAG = "BeamDAppWebView"
    private var appName: String = ""
    private var isActive = false
    private var dappAppDir: String = ""  // e.g., /data/.../dapps/{guid}/app

    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    init {
        setupWebView()
    }

    /**
     * Intercept the hardware BACK key at the View level — fires BEFORE RN's JS back handler.
     * RN's back handler needs the JS thread (blocked for 25s by heavy DApp WebView).
     * By intercepting here, we kill the WebView immediately, freeing the JS thread
     * so RN can process goBack() within milliseconds.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP && isActive) {
            Log.d(TAG, "BACK key intercepted — killing WebView immediately")
            pauseTimers()
            onPause()
            stopLoading()
            loadUrl("about:blank")
            // Don't consume — let the event propagate to RN's back handler
            // which will now process goBack() instantly since WebView is dead
            return false
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            onResume()
            resumeTimers()
            Log.d(TAG, "WebView resumed (visible)")
        } else {
            pauseTimers()
            onPause()
            Log.d(TAG, "WebView paused (hidden)")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true  // Required for file:// DApp loading
            // Security: disable cross-origin file access. Each DApp can only access
            // its own files via shouldInterceptRequest (which enforces path containment).
            allowFileAccessFromFileURLs = false  // Security: all file access goes through shouldInterceptRequest
            allowUniversalAccessFromFileURLs = false  // Block cross-origin requests from file:// URLs
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            databaseEnabled = true
            // Match official Beam Android wallet: use default viewport behavior.
            // DApps detect isMobile() via user-agent and render their own mobile layouts
            // at device-width (~360px). No viewport overrides needed.
            builtInZoomControls = true
            displayZoomControls = false // Hide +/- buttons, pinch gesture only
            setSupportZoom(true)
        }

        // Set WebView background to match Beam dark theme (prevents white flash and fills empty space)
        setBackgroundColor(android.graphics.Color.parseColor("#042548"))

        // Add the BEAM bridge — this is the key difference from the postMessage approach.
        // DApps call window.BEAM.callWalletApi(json) which goes directly to our @JavascriptInterface.
        addJavascriptInterface(BeamBridge(), "BEAM")

        webViewClient = DAppWebViewClient()
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val level = when (msg.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> "E"
                    ConsoleMessage.MessageLevel.WARNING -> "W"
                    else -> "D"
                }
                Log.println(
                    when (level) { "E" -> Log.ERROR; "W" -> Log.WARN; else -> Log.DEBUG },
                    "DAppConsole",
                    "[${msg.sourceId()?.substringAfterLast('/') ?: "?"}:${msg.lineNumber()}] ${msg.message()}"
                )
                return true
            }
        }

        // Enable WebView debugging only in debug builds
        WebView.setWebContentsDebuggingEnabled(com.privimemobile.BuildConfig.DEBUG)
    }

    /** Launch a DApp by name and URL */
    fun launchDApp(name: String, sourceUri: String) {
        this.appName = name
        this.isActive = true
        // Extract DApp's app/ directory from sourceUri for resolving absolute paths
        // sourceUri: file:///data/.../dapps/{guid}/app/index.html → dappAppDir: /data/.../dapps/{guid}/app
        val uriPath = android.net.Uri.parse(sourceUri).path ?: ""
        this.dappAppDir = uriPath.substringBeforeLast('/') // remove /index.html

        // Register this WebView as the active DApp response target
        DAppResponseRouter.setActiveWebView(this)

        // Create per-DApp API context in C++ (matches official wallet's launchApp).
        // CRITICAL: createApi() is ASYNC — it posts work to the reactor thread
        // (IWThread_createAppShaders) then sets _api via UI-thread callback.
        // If we load the DApp URL immediately, its invoke_contract calls go through
        // the OLD (PriviMe) API context. The old API gets destroyed mid-compilation
        // when the new API's callback fires, causing the response to be lost.
        // Fix: delay loadUrl by 1.5s to let the natural onPostFunctionToClientContext →
        // callMyMethod() cycle complete the API transition.
        val wallet = WalletManager.walletInstance
        if (wallet != null && Api.isWalletRunning()) {
            try {
                wallet.launchApp(name, "")
                Log.d(TAG, "launchApp('$name') — API context creation started (async)")
            } catch (e: Exception) {
                Log.w(TAG, "launchApp('$name') failed (non-fatal): ${e.message}")
            }

            // Delay URL load to let the async API context creation complete naturally.
            // The C++ reactor creates shaders manager → onPostFunctionToClientContext fires →
            // callMyMethod() dispatches the callback → _api is set to the new DApp API.
            postDelayed({
                Log.d(TAG, "Loading DApp '$name' from: $sourceUri (after API context delay)")
                loadUrl(sourceUri)
            }, 1500)
        } else {
            Log.w(TAG, "Wallet not ready for launchApp('$name')")
            Log.d(TAG, "Loading DApp '$name' from: $sourceUri")
            loadUrl(sourceUri)
        }
    }

    /**
     * Dispatch an API response to the DApp via JavaScript.
     * Called from DAppResponseRouter when a tracked call ID response arrives.
     *
     * Matches official wallet: evaluateJavascript dispatching CustomEvent("onCallWalletApiResult")
     */
    fun dispatchApiResponse(json: String) {
        // Base64 encode to safely pass through JS string
        val b64 = android.util.Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        post {
            evaluateJavascript("""
                (function() {
                    try {
                        var raw = atob('$b64');
                        var j = raw;
                        try { j = decodeURIComponent(escape(raw)); } catch(e) {}
                        // CustomEvent fires first (Android DApps listen for this),
                        // then our bridge listener emits on the Signal (desktop DApps using .connect())
                        document.dispatchEvent(new CustomEvent('onCallWalletApiResult', {detail: j}));
                    } catch(e) { console.error('[BEAM Bridge] dispatch error:', e); }
                })();
            """.trimIndent(), null)
        }
    }

    /** Clean up when DApp is closed */
    fun cleanup() {
        if (!isActive) return // Already cleaned up
        isActive = false
        DAppResponseRouter.setActiveWebView(null)

        // Stop JS execution. Do NOT call destroy() — it blocks the shared UI thread
        // for 20+ seconds on heavy DApps (AMM React+webpack), freezing the entire app.
        // loadUrl("about:blank") releases JS/DOM state. GC handles the rest.
        removeJavascriptInterface("BEAM")
        stopLoading()
        loadUrl("about:blank")
        Log.d(TAG, "DApp '$appName' cleaned up")

        // Restore PriviMe API context
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

    /**
     * The BEAM JavaScript interface — exposed to DApps as window.BEAM.
     *
     * This is the native bridge object. When a DApp calls:
     *   BEAM.callWalletApi(json)
     * or
     *   BEAM.api.callWalletApi(json)
     *
     * it goes directly to this @JavascriptInterface method → JNI → C++ wallet core.
     *
     * NOTE: The official wallet's IWebInterface also has callWalletApiResult(cb),
     * but DApps actually use document.addEventListener('onCallWalletApiResult') for responses,
     * so callWalletApiResult is just for the alternative callback pattern.
     */
    inner class BeamBridge {

        @JavascriptInterface
        fun callWalletApi(json: String) {
            val wallet = WalletManager.walletInstance
            if (wallet == null || !Api.isWalletRunning()) {
                Log.w(TAG, "callWalletApi: wallet not available")
                return
            }
            if (!WalletManager.isApiReady) {
                Log.w(TAG, "callWalletApi: API not ready yet")
                return
            }

            try {
                wallet.callWalletApi(json)
            } catch (e: Exception) {
                Log.e(TAG, "callWalletApi native error: ${e.message}", e)
            }
        }

        @JavascriptInterface
        fun callWalletApiResult(callback: String) {
            // Some DApps use this to register a named callback function.
            // We handle responses via CustomEvent dispatch instead.
            Log.d(TAG, "BEAM.callWalletApiResult registered (ignored — using CustomEvent)")
        }

        /**
         * Returns theme style JSON — matches official Beam wallet's IWebInterface.getStyle().
         * Called by DApps as BEAM.getStyle(). The JS shim overrides this with the full
         * style object, but this native method serves as a fallback.
         */
        @JavascriptInterface
        fun getStyle(): String {
            return """{"background_main":"#042548","background_main_top":"#035b8f","background_popup":"#00446c","navigation_background":"#000000","content_main":"#ffffff","content_secondary":"#8da1ad","accent_incoming":"#0bccf7","accent_outgoing":"#da68f5","active":"#00f6d2","validator_error":"#ff625c","appsGradientOffset":-174,"appsGradientTop":56}"""
        }
    }

    /**
     * Combined BEAM bridge shim — injected directly into DApp HTML via shouldInterceptRequest.
     * This is the ONLY reliable injection point — evaluateJavascript in onPageStarted/onPageFinished
     * has timing issues with file:// URLs intercepted by shouldInterceptRequest.
     *
     * Sets up:
     * 1. Viewport meta for mobile rendering
     * 2. BEAM.api — wraps flat native methods into nested BEAM.api.callWalletApi()
     * 3. BEAM.style — full Mainnet dark theme (from desktop wallet's Mainnet.qml)
     * 4. BEAM.getStyle() — returns style object (matches official wallet's IWebInterface)
     *
     * Must run BEFORE the DApp's own detection code (detectAndConnect / initBeam / etc.).
     */
    private val BEAM_API_SHIM = """<style>
html { height: 100vh !important; margin: 0 !important; padding: 0 !important; }
body { height: 100vh !important; margin: 0 !important; padding: 0 !important; background-color: #042548 !important; overflow: auto !important; }
#root { height: 100% !important; }
/* Prevent roulette column bets overlap on narrow screens — force table to scroll as one unit */
.table-body { min-width: 580px; }
</style>
<script>
(function() {
    // Protect <head> from innerHTML += destruction (Beam SDK applyStyles)
    // applyStyles() does document.head.innerHTML += '<meta..>' which re-parses all
    // head elements, destroying styled-components' CSSOM rules (insertRule).
    var __hDesc = Object.getOwnPropertyDescriptor(Element.prototype, 'innerHTML');
    if (__hDesc && __hDesc.set) {
        var __origGet = __hDesc.get;
        var __origSet = __hDesc.set;
        Object.defineProperty(document.head, 'innerHTML', {
            get: function() { return __origGet.call(this); },
            set: function(val) {
                // Heuristic: if new value is longer than current, assume += pattern
                var cur = __origGet.call(this);
                if (val.length > cur.length + 5) {
                    // Extract the appended part by finding the suffix
                    // Parse it and appendChild instead of re-parsing everything
                    var suffix = val.substring(cur.length);
                    var tmp = document.createElement('div');
                    tmp.innerHTML = suffix;
                    while (tmp.firstChild) {
                        document.head.appendChild(tmp.firstChild);
                    }
                    console.log('[BEAM Shim] Intercepted head innerHTML += (protected styled-components)');
                    return;
                }
                __origSet.call(this, val);
            },
            configurable: true
        });
    }
    // Beam Mainnet dark theme — exact colors from desktop wallet Mainnet.qml
    var STYLE = {
        background_main: "#042548",
        background_main_top: "#035b8f",
        background_second: "rgba(255,255,255,0.05)",
        background_details: "#09425e",
        background_button: "rgba(255,255,255,0.2)",
        background_popup: "#00446c",
        background_appstx: "#113051",
        navigation_background: "#000000",
        content_main: "#ffffff",
        content_secondary: "#8da1ad",
        content_disabled: "#889da9",
        content_opposite: "#032e49",
        accent_outgoing: "#da68f5",
        accent_incoming: "#0bccf7",
        accent_swap: "#39fdf2",
        accent_fail: "#ff746b",
        validator_warning: "#f4ce4a",
        validator_error: "#ff625c",
        active: "#00f6d2",
        passive: "#d6d9e0",
        online: "#00f6d2",
        section: "#2c5066",
        separator: "#33566b",
        table_header: "rgba(0,246,210,0.1)",
        row_selected: "#085469",
        background_row_even: "rgba(255,255,255,0.03)",
        background_row_odd: "rgba(255,255,255,0.05)",
        caps_warning: "#000000",
        coinPaneRight: "#00458f",
        coinPaneLeft: "#00f6d2",
        coinPaneBorder: "rgba(0,246,210,0.15)",
        swapStateIndicator: "#ff746b",
        swapDisconnectNode: "#f9605b",
        appsGradientOffset: -174,
        appsGradientTop: 56
    };

    // Global error handler — catch uncaught JS errors from DApps
    window.onerror = function(msg, src, line, col, err) {
        console.error('[BEAM DApp Error] ' + msg + ' at ' + (src||'?') + ':' + line + ':' + col);
        return false;
    };
    window.addEventListener('unhandledrejection', function(e) {
        console.error('[BEAM DApp Promise] ' + (e.reason ? (e.reason.message || e.reason) : 'unknown'));
    });

    // fetch() polyfill for file:// URLs — Android WebView rejects fetch("file://...")
    // but XHR works fine. Many Beam DApps (Nephrite, DEX, etc.) use fetch() to load shaders.
    var _origFetch = window.fetch;
    window.fetch = function(input, init) {
        var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
        // Polyfill all file:// URLs and all relative URLs when on a file:// page
        var isLocal = url.indexOf('file://') === 0 || (window.location.protocol === 'file:' && url.indexOf('://') < 0);
        if (isLocal) {
            return new Promise(function(resolve, reject) {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', url, true);
                xhr.responseType = 'arraybuffer';
                xhr.onload = function() {
                    var headers = {};
                    var allHeaders = xhr.getAllResponseHeaders();
                    if (allHeaders) {
                        allHeaders.trim().split(/[\r\n]+/).forEach(function(line) {
                            var parts = line.split(': ');
                            if (parts.length === 2) headers[parts[0].toLowerCase()] = parts[1];
                        });
                    }
                    resolve(new Response(xhr.response, {
                        status: xhr.status || 200,
                        statusText: xhr.statusText || 'OK',
                        headers: headers
                    }));
                };
                xhr.onerror = function() { reject(new TypeError('Network request failed: ' + url)); };
                xhr.send();
            });
        }
        return _origFetch.apply(window, arguments);
    };

    // Qt WebChannel signal emulation — desktop DApps use .connect(cb)
    function Signal() {
        this._cbs = [];
    }
    Signal.prototype.connect = function(cb) { this._cbs.push(cb); };
    Signal.prototype.disconnect = function(cb) {
        var i = this._cbs.indexOf(cb);
        if (i >= 0) this._cbs.splice(i, 1);
    };
    Signal.prototype.emit = function() {
        var args = arguments;
        for (var i = 0; i < this._cbs.length; i++) {
            try { this._cbs[i].apply(null, args); } catch(e) { console.error('[BEAM Signal]', e); }
        }
    };

    // Create the result signal globally so responses can reach it
    var resultSignal = new Signal();

    // Mutable container for native Java bridge reference.
    // WebView may re-inject the Java interface after our shim runs,
    // so the setter below captures re-injections transparently.
    var native = { ref: null };

    function setupBeam() {
        if (!window.BEAM) return false;

        // Capture native Java bridge
        native.ref = window.BEAM;

        // Build pure JS wrapper — all methods proxy through native.ref
        var wrapper = {
            callWalletApi: function(j) {
                try { return native.ref.callWalletApi(j); }
                catch(e) { console.error('[BEAM Bridge] callWalletApi error:', e.message); }
            },
            callWalletApiResult: function(cb) {
                try { return native.ref.callWalletApiResult(cb); }
                catch(e) {}
            },
            getStyle: function() { return STYLE; },
            api: {
                callWalletApi: function(j) {
                    try { return native.ref.callWalletApi(j); }
                    catch(e) { console.error('[BEAM Bridge] callWalletApi error:', e.message); }
                },
                callWalletApiResult: resultSignal
            },
            style: STYLE
        };

        // Lock window.BEAM to our wrapper via Object.defineProperty.
        // When WebView re-injects the Java interface, the setter captures the
        // new native reference but the getter always returns our wrapper.
        try {
            Object.defineProperty(window, 'BEAM', {
                get: function() { return wrapper; },
                set: function(v) {
                    if (v && v !== wrapper) {
                        try { if (typeof v.callWalletApi === 'function') native.ref = v; } catch(e) {}
                    }
                },
                configurable: true,
                enumerable: true
            });
        } catch(e) {
            window.BEAM = wrapper;
        }

        // Hook into CustomEvent so signal callbacks also fire
        document.addEventListener('onCallWalletApiResult', function(e) {
            resultSignal.emit(e.detail);
        });

        console.log('[BEAM Bridge] Ready — locked wrapper + api + style + fetch polyfill');

        // Pre-warm wallet UTXO cache — the first process_invoke_data triggers CompleteBalance()
        // which scans the UTXO database. Without warming, this takes 10-15s on Android due to
        // cold SQLite page cache. By triggering get_utxo early, the DB pages are in memory
        // when the DApp's first TX needs them.
        try {
            native.ref.callWalletApi(JSON.stringify({jsonrpc:'2.0',id:'_warmup_utxo',method:'get_utxo',params:{}}));
            native.ref.callWalletApi(JSON.stringify({jsonrpc:'2.0',id:'_warmup_assets',method:'assets_list',params:{}}));
        } catch(e) {}

        return true;
    }
    if (!setupBeam()) {
        var n = 0;
        var t = setInterval(function() {
            n++;
            if (setupBeam()) { clearInterval(t); }
            else if (n >= 100) { console.error('[BEAM Bridge] window.BEAM never appeared'); clearInterval(t); }
        }, 10);
    }
})();
</script>
"""

    /**
     * WebView client that handles DApp loading.
     * Injects BEAM.api shim into HTML via shouldInterceptRequest for guaranteed timing.
     */
    private inner class DAppWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return false
        }

        /**
         * Intercept file:// requests to:
         * 1. Inject BEAM.api shim into the main DApp HTML (index.html)
         * 2. Return all local files with proper HTTP 200 status
         *
         * Android WebView returns status 0 for file:// URLs, but DApps check
         * xhr.status === 200 (e.g., when loading app.wasm). By intercepting ALL
         * file:// requests and returning WebResourceResponse with explicit 200,
         * XHR/fetch will see the correct status code.
         */
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            if (!url.startsWith("file://")) {
                return super.shouldInterceptRequest(view, request)
            }

            val path = request.url.path ?: return null

            // Security: resolve file path and verify it stays within the DApp's directory.
            // Prevents path traversal (e.g., ../../other-dapp/app/index.html) that could
            // let a malicious DApp read other DApps' source code or app data.
            var file = File(path)
            if (dappAppDir.isNotEmpty()) {
                val dappRoot = File(dappAppDir).parentFile  // {guid}/ directory
                val dappRootPath = dappRoot?.canonicalPath ?: ""

                if (!file.exists()) {
                    // Try resolving relative to app/ dir first, then DApp root
                    val resolved = File(dappAppDir, path.trimStart('/'))
                    val resolvedRoot = if (dappRoot != null) File(dappRoot, path.trimStart('/')) else null

                    if (resolved.exists()) {
                        file = resolved
                    } else if (resolvedRoot != null && resolvedRoot.exists()) {
                        file = resolvedRoot
                    } else {
                        Log.w(TAG, "File not found: $path")
                        return null
                    }
                }

                // Verify resolved path is within the DApp's directory (prevent traversal)
                val canonicalPath = file.canonicalPath
                if (dappRootPath.isNotEmpty() && !canonicalPath.startsWith(dappRootPath + File.separator) && canonicalPath != dappRootPath) {
                    Log.w(TAG, "Path traversal BLOCKED: $path → $canonicalPath (outside $dappRootPath)")
                    return null
                }
            } else if (!file.exists()) {
                Log.w(TAG, "File not found: $path")
                return null
            }

            try {
                // HTML files: inject BEAM.api shim into <head>
                if (url.endsWith("/index.html") || url.endsWith(".html")) {
                    val html = file.readText(Charsets.UTF_8)
                    val needsShim = url.endsWith("/index.html")
                    val finalHtml = if (needsShim) {
                        val headIdx = html.indexOf("<head>", ignoreCase = true)
                        if (headIdx >= 0) {
                            val insertAt = headIdx + 6
                            html.substring(0, insertAt) + BEAM_API_SHIM + html.substring(insertAt)
                        } else {
                            val htmlIdx = html.indexOf("<html", ignoreCase = true)
                            if (htmlIdx >= 0) {
                                val tagEnd = html.indexOf('>', htmlIdx)
                                if (tagEnd >= 0) {
                                    val insertAt = tagEnd + 1
                                    html.substring(0, insertAt) + BEAM_API_SHIM + html.substring(insertAt)
                                } else {
                                    BEAM_API_SHIM + html
                                }
                            } else {
                                BEAM_API_SHIM + html
                            }
                        }
                    } else html

                    if (needsShim) Log.d(TAG, "Injected BEAM.api shim into DApp HTML ($path)")
                    return WebResourceResponse(
                        "text/html", "UTF-8", 200, "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        finalHtml.byteInputStream(Charsets.UTF_8)
                    )
                }

                // All other file:// resources: serve with proper 200 status
                val mimeType = when {
                    path.endsWith(".wasm") -> "application/wasm"
                    path.endsWith(".js") -> "application/javascript"
                    path.endsWith(".css") -> "text/css"
                    path.endsWith(".json") -> "application/json"
                    path.endsWith(".svg") -> "image/svg+xml"
                    path.endsWith(".png") -> "image/png"
                    path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                    path.endsWith(".gif") -> "image/gif"
                    path.endsWith(".woff") -> "font/woff"
                    path.endsWith(".woff2") -> "font/woff2"
                    path.endsWith(".ttf") -> "font/ttf"
                    path.endsWith(".otf") -> "font/otf"
                    path.endsWith(".eot") -> "application/vnd.ms-fontobject"
                    else -> "application/octet-stream"
                }
                val fileName = path.substringAfterLast('/')
                Log.d(TAG, "Serving file: $fileName ($mimeType, ${file.length()} bytes)")
                return WebResourceResponse(
                    mimeType, null, 200, "OK",
                    mapOf("Access-Control-Allow-Origin" to "*"),
                    file.inputStream()
                )
            } catch (e: Exception) {
                Log.e(TAG, "shouldInterceptRequest error for $path: ${e.message}")
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d(TAG, "DApp '$appName' page starting: $url")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(TAG, "DApp '$appName' page loaded: $url")

            // Fix viewport height for DApps that use height:100% chains.
            // CSS 100vh resolves to 0 before WebView layout; this sets explicit pixel values.
            view?.evaluateJavascript("""
                (function() {
                    function fixHeight() {
                        var vh = window.innerHeight;
                        if (vh > 0 && document.body.offsetHeight < 10) {
                            document.documentElement.style.setProperty('height', vh + 'px', 'important');
                            document.body.style.setProperty('height', vh + 'px', 'important');
                            document.body.style.setProperty('overflow', 'auto', 'important');
                            var r = document.getElementById('root');
                            if (r) r.style.setProperty('height', '100%', 'important');
                        }
                    }
                    fixHeight();
                    setTimeout(fixHeight, 100);
                    setTimeout(fixHeight, 500);
                    window.addEventListener('resize', function() {
                        var vh = window.innerHeight;
                        if (vh > 0) {
                            document.documentElement.style.setProperty('height', vh + 'px', 'important');
                            document.body.style.setProperty('height', vh + 'px', 'important');
                            var r = document.getElementById('root');
                            if (r) r.style.setProperty('height', '100%', 'important');
                        }
                    });
                })();
            """.trimIndent(), null)
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            Log.e(TAG, "WebView error: $errorCode - $description ($failingUrl)")
        }
    }
}
