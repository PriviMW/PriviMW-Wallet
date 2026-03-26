package com.mw.beam.beamwallet.core.entities

import com.mw.beam.beamwallet.core.entities.dto.PaymentInfoDTO
import com.mw.beam.beamwallet.core.entities.dto.TransactionParametersDTO
import com.mw.beam.beamwallet.core.entities.dto.WalletAddressDTO

/**
 * JNI wallet instance — holds native pointer to C++ wallet object.
 * Method signatures must match libwallet-jni.so exactly.
 */
data class Wallet(val _this: Long) {
    // Wallet status & sync
    external fun getWalletStatus()
    external fun syncWithNode()
    external fun isSynced(): Boolean
    external fun isConnectionTrusted(): Boolean

    // Transactions
    external fun getTransactions()
    external fun sendTransaction(receiver: String, comment: String?, amount: Long, fee: Long, assetId: Int, isOffline: Boolean)
    external fun cancelTx(id: String)
    external fun deleteTx(id: String)
    external fun getPaymentInfo(txID: String)
    external fun verifyPaymentInfo(paymentInfo: String): PaymentInfoDTO

    // Addresses
    external fun getAddresses(own: Boolean)
    external fun generateNewAddress()
    external fun saveAddress(address: WalletAddressDTO, own: Boolean)
    external fun updateAddress(addr: String, name: String, addressExpirationEnum: Int)
    external fun deleteAddress(walletID: String)
    external fun getPublicAddress()
    external fun generateRegularAddress(amount: Long, assetId: Int)
    external fun generateOfflineAddress(amount: Long, assetId: Int)
    external fun generateMaxPrivacyAddress(amount: Long, assetId: Int)

    // Token/address validation & parsing
    external fun isToken(token: String): Boolean
    external fun isAddress(address: String): Boolean
    external fun getTransactionParameters(token: String, requestInfo: Boolean): TransactionParametersDTO?

    // DApp / Contract API — this is the key method
    external fun callWalletApi(json: String)
    external fun contractInfoApproved(json: String)
    external fun contractInfoRejected(json: String)
    external fun appSupported(version: String, minVersion: String): Boolean
    external fun launchApp(name: String, url: String)

    // Wallet management
    external fun changeWalletPassword(password: String)
    external fun checkWalletPassword(password: String): Boolean
    external fun changeNodeAddress(address: String)
    external fun enableBodyRequests(enable: Boolean)
    external fun rescan()

    // UTXOs
    external fun getAllUtxosStatus()
    external fun selectCoins(amount: Long, fee: Long, isShielded: Boolean, assetId: Int)
    external fun calcChange(amount: Long, assetId: Int)
    external fun getCoinsByTx(txID: String)

    // Data import/export
    external fun exportOwnerKey(pass: String): String
    external fun importRecovery(path: String)
    external fun importDataFromJson(data: String)
    external fun exportDataToJson()
    external fun exportTxHistoryToCsv()

    // Settings
    external fun getMaxPrivacyLockTimeLimitHoursAsync()
    external fun getMaxPrivacyLockTimeLimitHours(): Long
    external fun setMaxPrivacyLockTimeLimitHours(hours: Long)
    external fun setCoinConfirmationsOffset(value: Long)
    external fun getCoinConfirmationsOffsetAsync()
    external fun getCoinConfirmationsOffset(): Long

    // Misc
    external fun getAssetInfo(id: Int)
    external fun getMaturityHours(id: Long): Long
    external fun callMyMethod()
    external fun clearLastWalletId()
    external fun getTransactionRate(txId: String, currencyId: Int, assetId: Long): Long

    // Notifications & rates
    external fun switchOnOffExchangeRates(isActive: Boolean)
    external fun switchOnOffNotifications(type: Int, isActive: Boolean)
    external fun getExchangeRates()
    external fun getNotifications()
    external fun markNotificationAsRead(id: String)
    external fun deleteNotification(id: String)

    // DEX / Asset Swap
    external fun getDexOrders()
    external fun loadDexOrderParams()
    external fun publishDexOrder(sbbsAddr: String, sbbsKeyIdx: Long, sendAssetId: Int, sendAmount: Long, sendSname: String, receiveAssetId: Int, receiveAmount: Long, receiveSname: String, expireMinutes: Int)
    external fun cancelDexOrder(orderId: String)
    external fun acceptDexOrder(orderId: String, sbbsId: String, sendAssetId: Int, sendAmount: Long, receiveAssetId: Int, receiveAmount: Long, fee: Long)
}
