package com.mw.beam.beamwallet.core.entities.dto

data class NotificationDTO(
    val id: String,
    val state: Int,
    val type: Int,
    val createTime: Long
)
