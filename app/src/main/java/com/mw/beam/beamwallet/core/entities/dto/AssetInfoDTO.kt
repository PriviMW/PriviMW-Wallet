package com.mw.beam.beamwallet.core.entities.dto

data class AssetInfoDTO(
    var id: Int,
    var unitName: String,
    var nthUnitName: String,
    var shortName: String,
    var shortDesc: String,
    var longDesc: String,
    var name: String,
    var site: String,
    var paper: String
)
