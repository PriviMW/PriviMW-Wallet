package com.mw.beam.beamwallet.core.entities.dto

data class TransactionParametersDTO(
    val isMaxPrivacy: Boolean,
    val isShielded: Boolean,
    val isPublicOffline: Boolean,
    val isPermanentAddress: Boolean,
    val isOffline: Boolean,
    val addressType: Int,
    val address: String,
    val identity: String,
    val amount: Long,
    val assetId: Int,
    val versionError: Boolean,
    val version: String
)
