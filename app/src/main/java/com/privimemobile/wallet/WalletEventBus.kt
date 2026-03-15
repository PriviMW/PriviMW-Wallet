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

    // Wallet status (balance, height, etc.) — StateFlow for always-current value
    private val _walletStatus = kotlinx.coroutines.flow.MutableStateFlow(WalletStatusEvent(0, 0, 0, 0))
    val walletStatus: kotlinx.coroutines.flow.StateFlow<WalletStatusEvent> = _walletStatus

    // Transaction list updates
    private val _transactions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transactions: SharedFlow<String> = _transactions.asSharedFlow()

    // Sync progress
    private val _syncProgress = MutableSharedFlow<SyncProgressEvent>(extraBufferCapacity = 1)
    val syncProgress: SharedFlow<SyncProgressEvent> = _syncProgress.asSharedFlow()

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

    // --- Emit functions (called from WalletListener on UI thread) ---

    fun emitWalletStatus(event: WalletStatusEvent) { _walletStatus.value = event }
    fun emitTransactions(json: String) { _transactions.tryEmit(json) }
    fun emitSyncProgress(event: SyncProgressEvent) { _syncProgress.tryEmit(event) }
    fun emitNodeConnection(event: NodeConnectionEvent) { _nodeConnection.value = event }
    fun emitApiResult(json: String) { _apiResult.tryEmit(json) }
    fun emitContractConsent(event: ContractConsentEvent) { _contractConsent.tryEmit(event) }
    fun emitSendConsent(event: SendConsentEvent) { _sendConsent.tryEmit(event) }
    fun emitAddresses(event: AddressesEvent) { _addresses.tryEmit(event) }
    fun emitAssetInfo(event: AssetInfoEvent) { _assetInfo.tryEmit(event) }
}

// Event data classes
data class WalletStatusEvent(
    val available: Long,
    val receiving: Long,
    val sending: Long,
    val maturing: Long,
    val height: Long = 0,
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
)
