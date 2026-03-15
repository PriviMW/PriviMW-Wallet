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
        status.forEach { asset ->
            uiHandler.post {
                WalletEventBus.emitWalletStatus(
                    WalletStatusEvent(
                        available = asset.available,
                        receiving = asset.receiving,
                        sending = asset.sending,
                        maturing = asset.maturing,
                        height = asset.system.height,
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
            arr.put(obj)
        }
        uiHandler.post { WalletEventBus.emitTransactions(arr.toString()) }
    }

    // === Sync Progress ===

    @JvmStatic
    fun onSyncProgressUpdated(done: Int, total: Int) {
        uiHandler.post { WalletEventBus.emitSyncProgress(SyncProgressEvent(done, total)) }
    }

    @JvmStatic
    fun onNodeSyncProgressUpdated(done: Int, total: Int) {
        uiHandler.post { WalletEventBus.emitSyncProgress(SyncProgressEvent(done, total)) }
    }

    // === Node Connection ===

    @JvmStatic
    fun onNodeConnectedStatusChanged(isNodeConnected: Boolean) {
        Log.d(TAG, "onNodeConnected: $isNodeConnected")
        uiHandler.post {
            WalletEventBus.emitNodeConnection(NodeConnectionEvent(connected = isNodeConnected))
            if (isNodeConnected) {
                WalletManager.setApiReady(true)
            }
        }
    }

    @JvmStatic
    fun onNodeConnectionFailed(error: Int) {
        Log.d(TAG, "onNodeConnectionFailed: $error")
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
        // Re-fetch addresses on change
    }

    @JvmStatic
    fun onGeneratedNewAddress(addr: WalletAddressDTO) {
        uiHandler.post {
            WalletEventBus.emitAddresses(AddressesEvent(true, addr.walletID))
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

    @JvmStatic fun onCantSendToExpired() { }
    @JvmStatic fun onStartedNode() { }
    @JvmStatic fun onStoppedNode() { }
    @JvmStatic fun onNodeCreated() { }
    @JvmStatic fun onNodeThreadFinished() { }
    @JvmStatic fun onFailedToStartNode() { Log.w(TAG, "onFailedToStartNode") }

    @JvmStatic fun onPaymentProofExported(txId: String, proof: PaymentInfoDTO) { }

    @JvmStatic fun onCoinsByTx(utxos: Array<UtxoDTO>?) { }
    @JvmStatic fun onNormalUtxoChanged(action: Int, utxos: Array<UtxoDTO>?) { }
    @JvmStatic fun onAllShieldedUtxoChanged(action: Int, utxos: Array<UtxoDTO>?) { }
    @JvmStatic fun onAllUtxoChanged(utxos: Array<UtxoDTO>?) { }

    @JvmStatic fun onChangeCalculated(amount: Long) { }
    @JvmStatic fun onCoinsSelected(explicitFee: Long, change: Long, minimalExplicitFee: Long, max: Long) { }
    @JvmStatic fun onNeedExtractShieldedCoins(value: Boolean) { }

    @JvmStatic fun onImportRecoveryProgress(done: Long, total: Long) {
        uiHandler.post { WalletEventBus.emitSyncProgress(SyncProgressEvent(done.toInt(), total.toInt())) }
    }

    @JvmStatic fun onImportDataFromJson(isOk: Boolean) { }
    @JvmStatic fun onExportDataToJson(data: String) { }

    @JvmStatic fun onExchangeRates(rates: Array<ExchangeRateDTO>?) { }

    @JvmStatic fun onNewVersionNotification(action: Int, notificationInfo: NotificationDTO, content: VersionInfoDTO) { }
    @JvmStatic fun onAddressChangedNotification(action: Int, notificationInfo: NotificationDTO, content: WalletAddressDTO) { }
    @JvmStatic fun onTransactionFailedNotification(action: Int, notificationInfo: NotificationDTO, content: TxDescriptionDTO) { }
    @JvmStatic fun onTransactionCompletedNotification(action: Int, notificationInfo: NotificationDTO, content: TxDescriptionDTO) { }
    @JvmStatic fun onBeamNewsNotification(action: Int) { }

    @JvmStatic fun onGetAddress(offlinePayments: Int) { }
    @JvmStatic fun onPublicAddress(value: String) { }
    @JvmStatic fun onMaxPrivacyAddress(value: String) { }
    @JvmStatic fun onRegularAddress(value: String) { }
    @JvmStatic fun onOfflineAddress(value: String) { }
    @JvmStatic fun onExportTxHistoryToCsv(value: String) { }
    @JvmStatic fun onExportContractTxHistoryToCsv(value: String) { }
}
