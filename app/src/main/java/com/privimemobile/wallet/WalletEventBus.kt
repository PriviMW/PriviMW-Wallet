package com.privimemobile.wallet

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Wallet event bus — replaces React Native's NativeEventEmitter.
 *
 * WalletListener (JNI callbacks) emits into these flows.
 * ViewModels collect from them in viewModelScope.
 * All emissions happen on the UI thread (via WalletListener's uiHandler).
 */
object WalletEventBus {

    // Per-asset status events (fires once per asset from onStatus)
    private val _walletStatus = MutableSharedFlow<WalletStatusEvent>(extraBufferCapacity = 16)
    val walletStatus: SharedFlow<WalletStatusEvent> = _walletStatus.asSharedFlow()

    // BEAM-only status (assetId=0) — convenience for screens that only need BEAM balance
    private val _beamStatus = kotlinx.coroutines.flow.MutableStateFlow(WalletStatusEvent(0, 0, 0, 0, 0, 0))
    val beamStatus: kotlinx.coroutines.flow.StateFlow<WalletStatusEvent> = _beamStatus

    // Per-asset balance map — persistent singleton (survives navigation, like RN Zustand store)
    val assetBalances = java.util.concurrent.ConcurrentHashMap<Int, WalletStatusEvent>()

    // Per-asset metadata cache — persistent singleton (like Beam wallet's AssetManager)
    val assetInfoCache = java.util.concurrent.ConcurrentHashMap<Int, AssetInfoEvent>()
    // Observable version counter — collect this in Compose to recompose when asset info updates
    private val _assetInfoVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    val assetInfoVersion: kotlinx.coroutines.flow.StateFlow<Int> = _assetInfoVersion
    // Track which asset IDs we've already requested info for (avoid duplicate JNI calls)
    private val requestedAssetIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    // Transaction list updates — StateFlow so detail screen always has current data
    private val _transactions = kotlinx.coroutines.flow.MutableStateFlow("[]")
    val transactions: kotlinx.coroutines.flow.StateFlow<String> = _transactions

    // Sync progress — StateFlow so UI always gets current value
    private val _syncProgress = kotlinx.coroutines.flow.MutableStateFlow(SyncProgressEvent(0, 0))
    val syncProgress: kotlinx.coroutines.flow.StateFlow<SyncProgressEvent> = _syncProgress

    // Node connection state — uses StateFlow so UI always has current value
    private val _nodeConnection = kotlinx.coroutines.flow.MutableStateFlow(NodeConnectionEvent(connected = false))
    val nodeConnection: kotlinx.coroutines.flow.StateFlow<NodeConnectionEvent> = _nodeConnection

    // API results (invoke_contract responses, wallet_status, etc.)
    private val _apiResult = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val apiResult: SharedFlow<String> = _apiResult.asSharedFlow()

    // Contract consent requests (TX approval)
    private val _contractConsent = MutableSharedFlow<ContractConsentEvent>(extraBufferCapacity = 1)
    val contractConsent: SharedFlow<ContractConsentEvent> = _contractConsent.asSharedFlow()

    // Send consent requests
    private val _sendConsent = MutableSharedFlow<SendConsentEvent>(extraBufferCapacity = 1)
    val sendConsent: SharedFlow<SendConsentEvent> = _sendConsent.asSharedFlow()

    // Addresses
    private val _addresses = MutableSharedFlow<AddressesEvent>(extraBufferCapacity = 1)
    val addresses: SharedFlow<AddressesEvent> = _addresses.asSharedFlow()

    // Asset info
    private val _assetInfo = MutableSharedFlow<AssetInfoEvent>(extraBufferCapacity = 8)
    val assetInfo: SharedFlow<AssetInfoEvent> = _assetInfo.asSharedFlow()

    // Payment proof results
    private val _paymentProof = MutableSharedFlow<PaymentProofEvent>(extraBufferCapacity = 1)
    val paymentProof: SharedFlow<PaymentProofEvent> = _paymentProof.asSharedFlow()

    // UTXO list — StateFlow so screen always has current data
    private val _utxos = kotlinx.coroutines.flow.MutableStateFlow("[]")
    val utxos: kotlinx.coroutines.flow.StateFlow<String> = _utxos

    // Export data results (wallet JSON backup, TX CSV)
    private val _exportData = MutableSharedFlow<ExportDataEvent>(extraBufferCapacity = 1)
    val exportData: SharedFlow<ExportDataEvent> = _exportData.asSharedFlow()

    // Coin selection results (fee calculation, change)
    private val _coinSelection = MutableSharedFlow<CoinSelectionEvent>(extraBufferCapacity = 1)
    val coinSelection: SharedFlow<CoinSelectionEvent> = _coinSelection.asSharedFlow()

    // Generic wallet events (import results, errors)
    private val _walletEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val walletEvent: SharedFlow<String> = _walletEvent.asSharedFlow()

    // --- Emit functions (called from WalletListener on UI thread) ---

    fun emitWalletStatus(event: WalletStatusEvent) {
        assetBalances[event.assetId] = event
        _walletStatus.tryEmit(event)
        if (event.assetId == 0) _beamStatus.value = event
        // Auto-request asset info for non-BEAM assets (like RN useBeamEvents + Beam AssetManager)
        if (event.assetId != 0 && requestedAssetIds.add(event.assetId)) {
            try {
                WalletManager.walletInstance?.getAssetInfo(event.assetId)
            } catch (_: Exception) {}
        }
    }
    fun emitTransactions(json: String) { _transactions.value = json }
    fun emitSyncProgress(event: SyncProgressEvent) { _syncProgress.value = event }
    fun emitNodeConnection(event: NodeConnectionEvent) { _nodeConnection.value = event }
    fun emitApiResult(json: String) { _apiResult.tryEmit(json) }
    fun emitContractConsent(event: ContractConsentEvent) { _contractConsent.tryEmit(event) }
    fun emitSendConsent(event: SendConsentEvent) { _sendConsent.tryEmit(event) }
    fun emitAddresses(event: AddressesEvent) { _addresses.tryEmit(event) }
    fun emitUtxos(json: String) { _utxos.value = json }
    fun emitPaymentProof(event: PaymentProofEvent) { _paymentProof.tryEmit(event) }
    fun emitAssetInfo(event: AssetInfoEvent) {
        assetInfoCache[event.id] = event
        _assetInfoVersion.value++
        _assetInfo.tryEmit(event)
    }
    fun emitExportData(json: String) { _exportData.tryEmit(ExportDataEvent("json", json)) }
    fun emitExportCsv(csv: String) { _exportData.tryEmit(ExportDataEvent("csv", csv)) }
    fun emitCoinSelection(event: CoinSelectionEvent) { _coinSelection.tryEmit(event) }
    fun emitWalletEvent(event: String) { _walletEvent.tryEmit(event) }
}

// Event data classes
data class WalletStatusEvent(
    val assetId: Int = 0,
    val available: Long,
    val receiving: Long,
    val sending: Long,
    val maturing: Long,
    val height: Long = 0,
    val shielded: Long = 0,
    val maxPrivacy: Long = 0,
)

data class SyncProgressEvent(val done: Int, val total: Int)

data class NodeConnectionEvent(val connected: Boolean, val error: Int = 0)

data class ContractConsentEvent(
    val request: String,
    val info: String,
    val amounts: String,
)

data class SendConsentEvent(
    val request: String,
    val info: String,
    val amounts: String,
)

data class AddressesEvent(val own: Boolean, val json: String)

data class AssetInfoEvent(
    val id: Int,
    val unitName: String,
    val nthUnitName: String,
    val shortName: String,
    val name: String,
) {
    /** Resolve display ticker: unitName > shortName > name > "Asset #id" */
    fun ticker(): String =
        unitName.ifEmpty { null } ?: shortName.ifEmpty { null } ?: name.ifEmpty { null } ?: "Asset #$id"
}

/** Global helper — resolve assetId to display ticker from cache */
fun assetTicker(assetId: Int): String {
    if (assetId == 0) return "BEAM"
    return WalletEventBus.assetInfoCache[assetId]?.ticker() ?: "Asset #$assetId"
}

/** Global helper — resolve assetId to full name from cache */
fun assetFullName(assetId: Int): String {
    if (assetId == 0) return "Beam"
    val info = WalletEventBus.assetInfoCache[assetId] ?: return "Asset #$assetId"
    return info.name.ifEmpty { null } ?: info.ticker()
}

data class PaymentProofEvent(
    val txId: String,
    val senderId: String,
    val receiverId: String,
    val amount: Long,
    val kernelId: String,
    val isValid: Boolean,
    val rawProof: String,
    val assetId: Int,
)

data class ExportDataEvent(val type: String, val data: String) // type: "json" or "csv"

data class CoinSelectionEvent(
    val explicitFee: Long = 0,
    val change: Long = 0,
    val minimalFee: Long = 0,
    val maxAmount: Long = 0,
)
