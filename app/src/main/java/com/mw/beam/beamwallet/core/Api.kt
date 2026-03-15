package com.mw.beam.beamwallet.core

import com.mw.beam.beamwallet.core.entities.Wallet

/**
 * Static JNI entry point — loads libwallet-jni.so and exposes wallet lifecycle methods.
 * Method signatures must match the native C++ JNI exports exactly.
 */
object Api {
    init {
        // Go's c-shared runtime installs a process-wide SIGSEGV handler for
        // goroutine stack growth. On Android this conflicts with Hermes JS
        // engine's guard-page handling, causing SEGV_ACCERR on mqt_v_js.
        // asyncpreemptoff=1 disables Go's SIGURG-based goroutine preemption,
        // reducing cross-thread signal interference.
        // cgocheck=0 disables cgo pointer checks (perf + stability).
        try {
            // asyncpreemptoff=1: disable Go SIGURG-based goroutine preemption
            //   (conflicts with Hermes JS engine signal handling)
            // cgocheck=0: disable cgo pointer checks (perf + stability)
            // madvdontneed=1: use MADV_DONTNEED instead of MADV_FREE
            //   (fixes Go heap metadata corruption on Android — "bad sweepgen")
            android.system.Os.setenv("GODEBUG", "asyncpreemptoff=1,cgocheck=0,madvdontneed=1", false)
            // Limit Go to 1 OS thread for goroutines — reduces GC contention
            // when Go c-shared runtime runs inside Android process
            android.system.Os.setenv("GOMAXPROCS", "1", false)
            // Force Go to dump full goroutine traces + core on crash
            android.system.Os.setenv("GOTRACEBACK", "crash", false)
        } catch (_: Exception) {}

        // Load Go IPFS shared library first — it uses GD TLS model
        // (compatible with dlopen) and must be loaded before wallet-jni
        // which depends on it
        System.loadLibrary("ipfs-bindings")
        System.loadLibrary("wallet-jni")
    }

    external fun createWallet(appVersion: String, nodeAddr: String, dbPath: String, pass: String, phrases: String, restore: Boolean = false): Wallet?
    external fun openWallet(appVersion: String, nodeAddr: String, dbPath: String, pass: String, enableBodyRequests: Boolean): Wallet?
    external fun isWalletInitialized(dbPath: String): Boolean
    external fun createMnemonic(): Array<String>
    external fun getDictionary(): Array<String>
    external fun checkReceiverAddress(address: String?): Boolean
    external fun closeWallet()
    external fun isWalletRunning(): Boolean
    external fun getDefaultPeers(): Array<String>
    external fun getLibVersion(): String
    external fun getIPFSPeerCount(): Int
}
