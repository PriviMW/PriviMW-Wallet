package com.mw.beam.beamwallet.core.entities.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SystemStateDTO(val hash: String, val height: Long) : Parcelable
