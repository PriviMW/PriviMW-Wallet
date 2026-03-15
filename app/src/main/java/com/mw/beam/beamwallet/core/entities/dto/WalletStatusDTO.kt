package com.mw.beam.beamwallet.core.entities.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletStatusDTO(
    val assetId: Int,
    val available: Long,
    val receiving: Long,
    val sending: Long,
    val maturing: Long,
    val shielded: Long,
    val maxPrivacy: Long,
    val updateLastTime: Long,
    val updateDone: Int,
    val updateTotal: Int,
    val system: SystemStateDTO
) : Parcelable
