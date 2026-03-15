package com.mw.beam.beamwallet.core.entities.dto

// JNI-constructed — field names must match C++ JNI code exactly
data class UtxoDTO(
    val id: Long,
    val stringId: String,
    val amount: Long,
    val status: Int,
    val maturity: Long,
    val keyType: Int,
    val confirmHeight: Long,
    val createTxId: String?,
    val spentTxId: String?,
    val txoID: Long,
    val assetId: Int,
    val isShielded: Boolean
)
