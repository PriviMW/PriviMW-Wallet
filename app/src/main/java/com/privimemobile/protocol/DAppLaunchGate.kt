package com.privimemobile.protocol

/**
 * Launch decision from local installed version vs cached store version (disk only).
 */
object DAppLaunchGate {

    enum class Plan {
        /** installed >= cached — open immediately; optional background verify. */
        OpenNow,
        /** cached > installed — install update before navigation. */
        UpdateFirst,
        /** No cache for this guid — full on-chain check before open. */
        FetchOnChainFirst,
    }

    fun plan(installedVersion: String, cachedStoreVersion: String?): Plan = when {
        cachedStoreVersion == null -> Plan.FetchOnChainFirst
        DAppStore.isVersionOlder(installedVersion, cachedStoreVersion) -> Plan.UpdateFirst
        else -> Plan.OpenNow
    }
}
