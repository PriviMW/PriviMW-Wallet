package com.mw.beam.beamwallet.core.entities.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TxDescriptionDTO(
    var id: String,
    var amount: Long,
    var fee: Long,
    var change: Long,
    var minHeight: Long,
    var peerId: String,
    var myId: String,
    var message: String?,
    var createTime: Long,
    var modifyTime: Long,
    var sender: Boolean,
    var status: Int,
    var kernelId: String,
    var selfTx: Boolean,
    var failureReason: Int,
    var identity: String?,
    var isPublicOffline: Boolean,
    var isMaxPrivacy: Boolean,
    var isShielded: Boolean,
    var token: String,
    var senderIdentity: String,
    var receiverIdentity: String,
    var receiverAddress: String,
    var senderAddress: String,
    var assetId: Int,
    var isDapps: Boolean,
    var appName: String?,
    var appID: String?,
    var contractCids: String?,
    var minConfirmations: Int?,
    var minConfirmationsProgress: String?,
    var assets: ArrayList<WalletStatusDTO>?  // JNI sends WalletStatusDTO[] — crashes on access, skip in Kotlin
) : Parcelable
