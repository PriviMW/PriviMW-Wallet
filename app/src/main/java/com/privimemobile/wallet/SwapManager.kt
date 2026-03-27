package com.privimemobile.wallet

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * SwapManager — Kotlin API for Beam DEX (asset swap) operations.
 *
 * All operations go through direct JNI calls to the C++ WalletClient,
 * NOT through the DApp JSON-RPC API (which blocks swap methods).
 *
 * Pattern: same as beam-ui desktop (getDexOrders, publishDexOrder, etc.)
 */
object SwapManager {
    private const val TAG = "SwapManager"
    private const val DEX_FEE_GROTH = 100_000L // 0.001 BEAM — hardcoded by Beam core

    /** Known real asset IDs paired with their canonical ticker. Used to filter fake assets with duplicate names. */
    private val knownAssets by lazy {
        mapOf(
            0 to "BEAM", 7 to "BEAMX", 9 to "TICO",
            36 to "bETH", 37 to "bUSDT", 38 to "bWBTC", 39 to "bDAI", 47 to "NPH",
        )
    }

    /**
     * Returns true if the receive side of an offer appears legitimate.
     * Checks BOTH the sname field (from the order directly, catches fakes before caching)
     * AND the resolved ticker (from assetTicker cache, catches fakes after caching).
     * A known ticker with a mismatched assetId is always fake.
     * The send side is not filtered — users can offer any asset they hold.
     */
    fun isLegitimateReceiveAsset(receiveAssetId: Int, receiveSname: String): Boolean {
        // Layer 1: check sname from order directly (works even if asset not cached yet)
        // sname may be formatted as "TICKER (ID)" e.g. "BEAMX (26)" — match as whole word only
        // so "BEAMX" doesn't false-match against "BEAM" prefix
        if (receiveSname.isNotEmpty()) {
            val canonicalEntry = knownAssets.entries.find { entry ->
                Regex("^\\Q${entry.value}\\E\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(receiveSname.trim())
            }
            if (canonicalEntry != null && canonicalEntry.key != receiveAssetId) return false
        }
        // Layer 2: check resolved ticker from cache (catches fakes after asset info is fetched)
        val effectiveTicker = assetTicker(receiveAssetId)
        if (effectiveTicker != "Asset #$receiveAssetId") {
            val tickerCanonical = knownAssets.entries.find { it.value.equals(effectiveTicker, ignoreCase = true) }
            if (tickerCanonical != null && tickerCanonical.key != receiveAssetId) return false
        }
        return true
    }

    /** Request current DEX order book from the network. Results arrive via onDexOrdersChanged callback. */
    fun refreshOrders() {
        Log.d(TAG, "refreshOrders()")
        WalletManager.walletInstance?.getDexOrders()
    }

    /** Load saved DEX params (SBBS key for orders). Call once on app startup. */
    fun loadParams() {
        Log.d(TAG, "loadParams()")
        WalletManager.walletInstance?.loadDexOrderParams()
    }

    /**
     * Create and publish a new swap offer.
     *
     * @param sbbsAddr SBBS address to listen for acceptance (generate via generateNewAddress first)
     * @param sbbsKeyIdx Key index for the SBBS address
     * @param sendAssetId Asset ID to sell (0 = BEAM)
     * @param sendAmount Amount to sell in groth
     * @param sendSname Short name of sell asset (e.g., "BEAM", "TICO")
     * @param receiveAssetId Asset ID to buy (0 = BEAM)
     * @param receiveAmount Amount to buy in groth
     * @param receiveSname Short name of buy asset
     * @param expireMinutes Offer expiry (30-720 minutes, i.e., 30min to 12h)
     */
    fun createOffer(
        sbbsAddr: String,
        sbbsKeyIdx: Long,
        sendAssetId: Int,
        sendAmount: Long,
        sendSname: String,
        receiveAssetId: Int,
        receiveAmount: Long,
        receiveSname: String,
        expireMinutes: Int,
    ) {
        require(sendAssetId != receiveAssetId) { "Cannot swap same asset" }
        require(sendAmount > 0) { "Send amount must be positive" }
        require(receiveAmount > 0) { "Receive amount must be positive" }
        require(expireMinutes in 30..720) { "Expiry must be 30-720 minutes" } // validated as minutes, converted to seconds below

        val expireSeconds = expireMinutes * 60 // C++ DexOrder expects seconds, not minutes
        Log.d(TAG, "createOffer: sell $sendAmount of asset $sendAssetId for $receiveAmount of asset $receiveAssetId, expires ${expireMinutes}m (${expireSeconds}s)")
        WalletManager.walletInstance?.publishDexOrder(
            sbbsAddr, sbbsKeyIdx,
            sendAssetId, sendAmount, sendSname,
            receiveAssetId, receiveAmount, receiveSname,
            expireSeconds,
        )
    }

    /**
     * Cancel your own swap offer.
     *
     * @param orderId 32-char hex UUID of the order
     */
    fun cancelOffer(orderId: String) {
        require(orderId.length == 32) { "Invalid order ID length" }
        Log.d(TAG, "cancelOffer: $orderId")
        WalletManager.walletInstance?.cancelDexOrder(orderId)
    }

    /**
     * Accept someone else's swap offer. Creates a DexTransaction.
     * The wallet's TX approval dialog will show — user must confirm.
     *
     * @param order The DexOrder to accept
     */
    fun acceptOffer(order: DexOrder) {
        require(!order.isMine) { "Cannot accept your own offer" }
        val wallet = WalletManager.walletInstance
        Log.d(TAG, "acceptOffer: ${order.orderId}, wallet=${wallet != null}, sbbsId=${order.sbbsId.take(20)}...")
        if (wallet == null) {
            Log.e(TAG, "acceptOffer: wallet is null!")
            return
        }
        try {
            // JSON uses perspective-aware fields: sendAssetId = what viewer sends, receiveAssetId = what viewer receives
            // Pass SBBS ID as-is from C++ (82 hex chars = 41 bytes WalletID)
            Log.d(TAG, "acceptOffer PARAMS: orderId=${order.orderId} sbbsId=${order.sbbsId} (${order.sbbsId.length} chars) coinMy=${order.sendAssetId} amtMy=${order.sendAmount} coinPeer=${order.receiveAssetId} amtPeer=${order.receiveAmount} fee=$DEX_FEE_GROTH")
            wallet.acceptDexOrder(
                order.orderId,
                order.sbbsId,
                order.sendAssetId,     // coinMy: what taker sends
                order.sendAmount,      // amountMy
                order.receiveAssetId,  // coinPeer: what taker receives
                order.receiveAmount,   // amountPeer
                DEX_FEE_GROTH,
            )
            Log.d(TAG, "acceptOffer: JNI call completed")
        } catch (e: Exception) {
            Log.e(TAG, "acceptOffer FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Parse JSON array from onDexOrdersChanged callback into DexOrder list. */
    fun parseOrders(json: String): List<DexOrder> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    DexOrder(
                        orderId = o.getString("orderID"),
                        sbbsId = o.getString("sbbsID"),
                        sendAssetId = o.getInt("sendAssetId"),
                        sendAmount = o.getLong("sendAmount"),
                        sendSname = o.optString("sendSname", ""),
                        receiveAssetId = o.getInt("receiveAssetId"),
                        receiveAmount = o.getLong("receiveAmount"),
                        receiveSname = o.optString("receiveSname", ""),
                        createTime = o.getLong("createTime"),
                        expireTime = o.getLong("expireTime"),
                        isMine = o.getBoolean("isMine"),
                        isAccepted = o.getBoolean("isAccepted"),
                        isCanceled = o.getBoolean("isCanceled"),
                        isExpired = o.getBoolean("isExpired"),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse order at index $i: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse orders JSON: ${e.message}")
            emptyList()
        }
    }
}

/** A DEX swap order from the order book. */
data class DexOrder(
    val orderId: String,
    val sbbsId: String,
    val sendAssetId: Int,
    val sendAmount: Long,
    val sendSname: String,
    val receiveAssetId: Int,
    val receiveAmount: Long,
    val receiveSname: String,
    val createTime: Long,
    val expireTime: Long,
    val isMine: Boolean,
    val isAccepted: Boolean,
    val isCanceled: Boolean,
    val isExpired: Boolean,
) {
    /** Exchange rate: how much of receiveAsset per 1 sendAsset */
    val rate: Double get() {
        if (sendAmount <= 0) return 0.0
        return receiveAmount.toDouble() / sendAmount.toDouble()
    }

    /** Time remaining in seconds (0 if expired, -1 if no expiry set) */
    fun remainingSeconds(): Long {
        if (expireTime <= 0) return -1 // no expiry data
        val now = System.currentTimeMillis() / 1000
        return maxOf(0, expireTime - now)
    }

    /** Is this order still active (not accepted, cancelled, or expired)? */
    val isActive: Boolean get() {
        if (isAccepted || isCanceled || isExpired) return false
        val remaining = remainingSeconds()
        return remaining != 0L // -1 (no expiry) or >0 = active, 0 = expired
    }
}
