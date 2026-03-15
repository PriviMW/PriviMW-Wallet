package com.mw.beam.beamwallet.core.entities.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletAddressDTO(
    val walletID: String,
    var label: String,
    var category: String,
    val createTime: Long,
    var duration: Long,
    val own: Long,
    var identity: String,
    var address: String
) : Parcelable
