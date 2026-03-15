package com.mw.beam.beamwallet.core.entities.dto

data class ExchangeRateDTO(
    val fromName: String,
    val toName: String,
    val rate: Long,
    val updateTime: Long
)
