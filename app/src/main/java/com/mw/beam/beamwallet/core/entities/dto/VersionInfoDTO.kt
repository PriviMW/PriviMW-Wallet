package com.mw.beam.beamwallet.core.entities.dto

data class VersionInfoDTO(
    val application: Int,
    val versionMajor: Long,
    val versionMinor: Long,
    val versionRevision: Long
)
