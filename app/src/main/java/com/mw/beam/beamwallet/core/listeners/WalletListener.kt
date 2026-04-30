package com.mw.beam.beamwallet.core.listeners

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mw.beam.beamwallet.core.entities.dto.*
import com.privimemobile.dapp.DAppResponseRouter
import com.privimemobile.dapp.NativeTxApprovalDialog
import com.privimemobile.wallet.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Receives JNI callbacks from libwallet-jni.so C++ core.
 * All methods are @JvmStatic — the native code calls them by exact signature.
 *
 * Events are emitted to [WalletEventBus] (Kotlin SharedFlow) instead of
 * React Native's NativeEventEmitter. ViewModels collect from the bus directly.
 */
object WalletListener {
    private val TAG = "BeamWalletListener"
    private val uiHandler = Handler(Looper.getMainLooper())

    // Dedicated thread for callMyMethod() dispatch — avoids UI thread congestion.
    private val walletThread = android.os.HandlerThread("BeamWalletDispatch").apply { start() }
    private val walletHandler = android.os.Handler(walletThread.looper)

    // === Wallet Status ===

    @JvmStatic
    fun onStatus(status: Array<WalletStatusDTO>?) {
        if (status == null) return
        // Mark data received for error 4 suppression (matches RN lastDataTs pattern)
        com.privimemobile.protocol.NodeReconnect.onDataReceived()
        status.forEach { asset ->
            uiHandler.post {
                WalletEventBus.emitWalletStatus(
                    WalletStatusEvent(
                        assetId = asset.assetId,
                        available = asset.available,
                        receiving = asset.receiving,
                        sending = asset.sending,
                        maturing = asset.maturing,
                        height = asset.system.height,
                        shielded = asset.shielded,
                        maxPrivacy = asset.maxPrivacy,
                    )
                )
            }
        }
    }

    @JvmStatic
    fun onAssetInfo(info: AssetInfoDTO) {
        uiHandler.post {
            WalletEventBus.emitAssetInfo(
                AssetInfoEvent(info.id, info.unitName, info.nthUnitName, info.shortName, info.name)
            )
        }
    }

    // === Transactions ===

    @JvmStatic
    fun onTxStatus(action: Int, tx: Array<TxDescriptionDTO>?) {
        com.privimemobile.protocol.NodeReconnect.onDataReceived()
        val arr = JSONArray()
        tx?.forEach { t ->
            val obj = JSONObject()
            obj.put("txId", t.id)
            obj.put("amount", t.amount)
            obj.put("fee", t.fee)
            obj.put("sender", t.sender)
            obj.put("status", t.status)
            obj.put("peerId", t.peerId ?: "")
            obj.put("myId", t.myId ?: "")
            obj.put("message", t.message ?: "")
            obj.put("createTime", t.createTime)
            obj.put("assetId", t.assetId)
            obj.put("kernelId", t.kernelId ?: "")
            obj.put("isShielded", t.isShielded)
            obj.put("isMaxPrivacy", t.isMaxPrivacy)
            obj.put("isPublicOffline", t.isPublicOffline)
            obj.put("failureReason", t.failureReason)
            obj.put("selfTx", t.selfTx)
            obj.put("minConfirmations", t.minConfirmations ?: 0)
            obj.put("minConfirmationsProgress", t.minConfirmationsProgress ?: "")
            obj.put("token", t.token ?: "")
            obj.put("senderAddress", t.senderAddress ?: "")
            obj.put("receiverAddress", t.receiverAddress ?: "")
            obj.put("senderIdentity", t.senderIdentity ?: "")
            obj.put("receiverIdentity", t.receiverIdentity ?: "")
            obj.put("isDapps", t.isDapps)
            obj.put("appName", t.appName ?: "")
            obj.put("appID", t.appID ?: "")
            obj.put("contractCids", t.contractCids ?: "")
            if (t.isDapps) {
                Log.d(TAG, "DApp TX '${t.appName}': sender=${t.sender}, amount=${t.amount}, assets=${t.assets?.javaClass?.name ?: "null"}, size=${t.assets?.size ?: -1}")
                try {
                    val assetsList = t.assets
                    if (assetsList != null && assetsList.isNotEmpty()) {
                        val assetsArr = JSONArray()
                        assetsList.forEach { a ->
                            Log.d(TAG, "  contractAsset: id=${a.assetId} sending=${a.sending} receiving=${a.receiving}")
                            val aObj = JSONObject()
                            aObj.put("assetId", a.assetId)
                            aObj.put("sending", a.sending)
                            aObj.put("receiving", a.receiving)
                            assetsArr.put(aObj)
                        }
                        obj.put("contractAssets", assetsArr)
                    } else {
                        Log.d(TAG, "  assets list is null or empty")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Contract assets error: ${e.javaClass.simpleName}: ${e.message}", e)
                }
            }
            arr.put(obj)
        }
        val json = arr.toString()
        uiHandler.post {
            // action 3 = reset (full replace), otherwise merge to avoid partial updates
            // wiping out the full transaction list (e.g. after sendTransaction).
            if (action == 3) WalletEventBus.emitTransactions(json)
            else WalletEventBus.mergeTransactions(json)
        }
    }

    // === Sync Progress ===

    @JvmStatic
    fun onSyncProgressUpdated(done: Int, total: Int) {
        com.privimemobile.protocol.NodeReconnect.onDataReceived()
        uiHandler.post { WalletEventBus.emitSyncProgress(SyncProgressEvent(done, total)) }
    }

    @JvmStatic
    fun onNodeSyncProgressUpdated(done: Int, total: Int) {
        com.privimemobile.protocol.NodeReconnect.onDataReceived()
        uiHandler.post { WalletEventBus.emitSyncProgress(SyncProgressEvent(done, total)) }
    }

    // === Node Connection ===

    @JvmStatic
    fun onNodeConnectedStatusChanged(isNodeConnected: Boolean) {
        Log.d(TAG, "onNodeConnected: $isNodeConnected")
        if (isNodeConnected) {
            // Mark data received — connection itself proves data flow
            com.privimemobile.protocol.NodeReconnect.onDataReceived()
        }
        uiHandler.post {
            WalletEventBus.emitNodeConnection(NodeConnectionEvent(connected = isNodeConnected))
            if (isNodeConnected) {
                WalletManager.setApiReady(true)
            }
        }
    }

    @JvmStatic
    fun onNodeConnectionFailed(error: Int) {
        val timeSinceData = System.currentTimeMillis() - com.privimemobile.protocol.NodeReconnect.lastDataTs

        if (error == 4) {
            // Error 4 = secondary connection timeout (Beam 7.x).
            // These fire frequently even when primary connection is fine and TXs work.
            // Never let error 4 alone flip the UI to disconnected — use 30s grace period,
            // and beyond that just poke the wallet. Real disconnects are caught by
            // onNodeConnectedStatusChanged(false) or the periodic health check.
            if (timeSinceData < 30_000) return
            Log.d(TAG, "onNodeConnectionFailed: error 4, stale data (${timeSinceData}ms) — poking wallet")
            com.privimemobile.protocol.NodeReconnect.pokeWallet()
            return
        }

        // Non-error-4 failures: 10s grace, then full disconnect + reconnect
        if (timeSinceData < 10_000) return
        Log.d(TAG, "onNodeConnectionFailed: $error (timeSinceData=${timeSinceData}ms)")
        com.privimemobile.protocol.NodeReconnect.onConnectionFailed(error)
        uiHandler.post {
            WalletEventBus.emitNodeConnection(NodeConnectionEvent(connected = false, error = error))
        }
    }

    // === Addresses ===

    @JvmStatic
    fun onAddresses(own: Boolean, addresses: Array<WalletAddressDTO>?) {
        if (addresses == null) return
        val arr = JSONArray()
        addresses.forEach { a ->
            val obj = JSONObject()
            obj.put("walletID", a.walletID ?: "")
            obj.put("label", a.label ?: "")
            obj.put("createTime", a.createTime)
            obj.put("duration", a.duration)
            obj.put("own", a.own)
            obj.put("identity", a.identity ?: "")
            obj.put("address", a.address ?: "")
            arr.put(obj)
        }
        uiHandler.post { WalletEventBus.emitAddresses(AddressesEvent(own, arr.toString())) }
    }

    @JvmStatic
    fun onAddressesChanged(action: Int, addresses: Array<WalletAddressDTO>?) {
        // Re-fetch own addresses to update UI
        uiHandler.post {
            try {
                WalletManager.walletInstance?.getAddresses(true)
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun onGeneratedNewAddress(addr: WalletAddressDTO) {
        Log.d(TAG, "onGeneratedNewAddress: walletID=${addr.walletID.take(20)}... ownID=${addr.own}")
        // Re-fetch full address list so UI gets updated array
        uiHandler.post {
            try {
                WalletManager.walletInstance?.getAddresses(true)
            } catch (_: Exception) {}
            WalletEventBus.emitNewAddress(addr.walletID, addr.own)
        }
    }

    // === DApp API ===

    @JvmStatic
    fun sendDAOApiResult(info: String) {
        uiHandler.post {
            val consumed = DAppResponseRouter.onApiResult(info)
            if (!consumed) {
                WalletEventBus.emitApiResult(info)
            }
        }
    }

    @JvmStatic
    fun approveContractInfo(info: ContractConsentDTO) {
        uiHandler.post {
            NativeTxApprovalDialog.show(info.request, info.info, info.amounts, false)
        }
    }

    @JvmStatic
    fun approveSend(info: ContractConsentDTO) {
        uiHandler.post {
            NativeTxApprovalDialog.show(info.request, info.info, info.amounts, true)
        }
    }

    // === callMyMethod dispatch ===

    @JvmStatic
    fun onPostFunctionToClientContext(isOk: Boolean) {
        walletHandler.post {
            try {
                WalletManager.walletInstance?.callMyMethod()
            } catch (e: Exception) {
                Log.w(TAG, "callMyMethod failed: ${e.message}")
            }
        }
    }

    // === Other callbacks (must exist for JNI) ===

    @JvmStatic fun onCantSendToExpired() {
        Log.w(TAG, "onCantSendToExpired")
        uiHandler.post { WalletEventBus.emitWalletEvent("cant_send_expired") }
    }
    @JvmStatic fun onStartedNode() { }
    @JvmStatic fun onStoppedNode() { }
    @JvmStatic fun onNodeCreated() { }
    @JvmStatic fun onNodeThreadFinished() { }
    @JvmStatic fun onFailedToStartNode() { Log.w(TAG, "onFailedToStartNode") }

    @JvmStatic fun onPaymentProofExported(txId: String, proof: PaymentInfoDTO) {
        uiHandler.post {
            WalletEventBus.emitPaymentProof(
                PaymentProofEvent(
                    txId = txId,
                    senderId = proof.senderId,
                    receiverId = proof.receiverId,
                    amount = proof.amount,
                    kernelId = proof.kernelId,
                    isValid = proof.isValid,
                    rawProof = proof.rawProof,
                    assetId = proof.assetId,
                )
            )
        }
    }

    // Accumulated UTXOs merged across callbacks — partial callbacks (normal, shielded)
    // fire in sequence and would overwrite each other if emitted independently.
    // Instead we merge into this map and emit the union. onAllUtxoChanged resets it.
    private val mergedUtxos = java.util.concurrent.ConcurrentHashMap<Long, JSONObject>()

    @JvmStatic fun onCoinsByTx(utxos: Array<UtxoDTO>?) { utxos?.let { mergeAndEmitUtxos(it) } }
    @JvmStatic fun onNormalUtxoChanged(action: Int, utxos: Array<UtxoDTO>?) { utxos?.let { mergeAndEmitUtxos(it) } }
    @JvmStatic fun onAllShieldedUtxoChanged(action: Int, utxos: Array<UtxoDTO>?) { utxos?.let { mergeAndEmitUtxos(it) } }
    @JvmStatic fun onAllUtxoChanged(utxos: Array<UtxoDTO>?) {
        utxos?.let {
            mergedUtxos.clear()
            it.forEach { u -> mergedUtxos[u.id] = utxoToJson(u) }
            emitMergedUtxos()
        }
    }

    private fun utxoToJson(u: UtxoDTO): JSONObject {
        val obj = JSONObject()
        obj.put("id", u.id)
        obj.put("stringId", u.stringId)
        obj.put("amount", u.amount)
        obj.put("status", u.status)
        obj.put("maturity", u.maturity)
        obj.put("confirmHeight", u.confirmHeight)
        obj.put("createTxId", u.createTxId ?: "")
        obj.put("spentTxId", u.spentTxId ?: "")
        obj.put("assetId", u.assetId)
        obj.put("isShielded", u.isShielded)
        return obj
    }

    private fun mergeAndEmitUtxos(utxos: Array<UtxoDTO>) {
        utxos.forEach { u -> mergedUtxos[u.id] = utxoToJson(u) }
        emitMergedUtxos()
    }

    private fun emitMergedUtxos() {
        val arr = JSONArray()
        mergedUtxos.values.forEach { arr.put(it) }
        uiHandler.post { WalletEventBus.emitUtxos(arr.toString()) }
    }

    @JvmStatic fun onChangeCalculated(amount: Long) {
        uiHandler.post { WalletEventBus.emitCoinSelection(CoinSelectionEvent(change = amount)) }
    }
    @JvmStatic fun onCoinsSelected(explicitFee: Long, change: Long, minimalExplicitFee: Long, max: Long) {
        uiHandler.post {
            WalletEventBus.emitCoinSelection(CoinSelectionEvent(
                explicitFee = explicitFee, change = change,
                minimalFee = minimalExplicitFee, maxAmount = max,
            ))
        }
    }
    @JvmStatic fun onNeedExtractShieldedCoins(value: Boolean) { }

    @JvmStatic fun onImportRecoveryProgress(done: Long, total: Long) {
        uiHandler.post { WalletEventBus.emitSyncProgress(SyncProgressEvent(done.toInt(), total.toInt())) }
    }

    @JvmStatic fun onImportDataFromJson(isOk: Boolean) {
        Log.d(TAG, "onImportDataFromJson: $isOk")
        uiHandler.post { WalletEventBus.emitWalletEvent(if (isOk) "import_ok" else "import_failed") }
        if (isOk) {
            uiHandler.post {
                try {
                    WalletManager.walletInstance?.getWalletStatus()
                    WalletManager.walletInstance?.getTransactions()
                    WalletManager.walletInstance?.getAddresses(true)
                } catch (_: Exception) {}
            }
        }
    }

    @JvmStatic fun onTransactionCompletedNotification(action: Int, notificationInfo: NotificationDTO, content: TxDescriptionDTO) {
        // Refresh state on TX completion (matches RN onTransactionCompleted)
        uiHandler.post {
            try {
                WalletManager.walletInstance?.getWalletStatus()
                WalletManager.walletInstance?.getTransactions()
            } catch (_: Exception) {}
        }
    }
    @JvmStatic fun onTransactionFailedNotification(action: Int, notificationInfo: NotificationDTO, content: TxDescriptionDTO) {
        // Refresh state on TX failure (matches RN onTransactionFailed)
        uiHandler.post {
            try {
                WalletManager.walletInstance?.getWalletStatus()
                WalletManager.walletInstance?.getTransactions()
            } catch (_: Exception) {}
        }
    }
    @JvmStatic fun onExportDataToJson(data: String) {
        Log.d(TAG, "onExportDataToJson: ${data.length} chars")
        uiHandler.post { WalletEventBus.emitExportData(data) }
    }

    @JvmStatic fun onExchangeRates(rates: Array<ExchangeRateDTO>?) {
        if (rates == null || rates.isEmpty()) return
        val rateMap = mutableMapOf<String, Double>()
        for (r in rates) {
            // rate is in groth (1 BEAM = 100_000_000 groth), convert to decimal
            val key = "${r.fromName}_${r.toName}"
            rateMap[key] = r.rate.toDouble() / 100_000_000.0
        }
        Log.d(TAG, "onExchangeRates: ${rateMap.size} pairs")
        uiHandler.post { WalletEventBus.emitExchangeRates(rateMap) }
    }

    @JvmStatic fun onDexOrdersChanged(action: Int, ordersJson: String) {
        Log.d(TAG, "onDexOrdersChanged: action=$action, json=${ordersJson.take(100)}...")
        uiHandler.post { WalletEventBus.emitDexOrders(action, ordersJson) }
    }

    @JvmStatic fun onNewVersionNotification(action: Int, notificationInfo: NotificationDTO, content: VersionInfoDTO) { }
    @JvmStatic fun onAddressChangedNotification(action: Int, notificationInfo: NotificationDTO, content: WalletAddressDTO) { }
    @JvmStatic fun onBeamNewsNotification(action: Int) { }

    @JvmStatic fun onGetAddress(offlinePayments: Int) { }
    @JvmStatic fun onPublicAddress(value: String) {
        Log.d(TAG, "onPublicAddress: ${value.take(20)}...")
        uiHandler.post { WalletEventBus.emitAddresses(AddressesEvent(own = true, json = value)) }
    }
    @JvmStatic fun onMaxPrivacyAddress(value: String) {
        Log.d(TAG, "onMaxPrivacyAddress: ${value.take(20)}...")
        uiHandler.post { WalletEventBus.emitAddresses(AddressesEvent(own = true, json = value)) }
    }
    @JvmStatic fun onRegularAddress(value: String) {
        Log.d(TAG, "onRegularAddress: ${value.take(20)}...")
        uiHandler.post { WalletEventBus.emitAddresses(AddressesEvent(own = true, json = value)) }
    }
    @JvmStatic fun onOfflineAddress(value: String) {
        Log.d(TAG, "onOfflineAddress: ${value.take(20)}...")
        uiHandler.post { WalletEventBus.emitAddresses(AddressesEvent(own = true, json = value)) }
    }
    @JvmStatic fun onExportTxHistoryToCsv(value: String) {
        Log.d(TAG, "onExportTxHistoryToCsv: ${value.length} chars")
        uiHandler.post { flushCsv("transactions", value) }
    }
    @JvmStatic fun onExportAtomicSwapTxHistoryToCsv(value: String) {
        Log.d(TAG, "onExportAtomicSwapTxHistoryToCsv: ${value.length} chars")
        uiHandler.post { flushCsv("atomic_swap", value) }
    }
    @JvmStatic fun onExportAssetsSwapTxHistoryToCsv(value: String) {
        Log.d(TAG, "onExportAssetsSwapTxHistoryToCsv: ${value.length} chars")
        uiHandler.post { flushCsv("assets_swap", value) }
    }
    @JvmStatic fun onExportContractTxHistoryToCsv(value: String) {
        Log.d(TAG, "onExportContractTxHistoryToCsv: ${value.length} chars")
        uiHandler.post { flushCsv("contracts", value) }
    }

    // CSV export accumulator — native exportTxHistoryToCsv() fires up to 4 callbacks
    // (regular, atomic swap, assets swap, contracts). Contract is always last.
    // We accumulate all CSVs and emit a bundle when contracts arrive.
    private val pendingCsvs = mutableMapOf<String, String>()
    private fun flushCsv(name: String, csv: String) {
        pendingCsvs[name] = csv
        if (name == "contracts") {
            val bundle = pendingCsvs.toMap()
            pendingCsvs.clear()
            WalletEventBus.emitExportCsvBundle(bundle)
        }
    }

    // === SBBS Instant Message (fired by C++ core when SBBS message arrives) ===

    @JvmStatic fun onInstantMessage(timestamp: Long, sender: String, message: String, isIncome: Boolean) {
        Log.d(TAG, "onInstantMessage: income=$isIncome sender=${sender.take(16)}... ts=$timestamp")
        if (isIncome) {
            // Immediately trigger SBBS poll to process the message through our Room pipeline
            uiHandler.post {
                if (com.privimemobile.chat.ChatService.initialized.value) {
                    com.privimemobile.chat.ChatService.sbbs.pollNow()
                }
            }
        }
    }
}
