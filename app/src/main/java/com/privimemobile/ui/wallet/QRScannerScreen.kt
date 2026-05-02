package com.privimemobile.ui.wallet

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.privimemobile.R
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private const val TAG = "QRScanner"

/** Parse QR content — handles both raw addresses and Beam URI format (beam:<address>?amount=X) */
fun parseBeamQr(raw: String): String {
    val trimmed = raw.trim()
    val withoutPrefix = when {
        trimmed.startsWith("beam://") -> trimmed.removePrefix("beam://")
        trimmed.startsWith("beam:") -> trimmed.removePrefix("beam:")
        else -> trimmed
    }
    return withoutPrefix.substringBefore("?")
}

/**
 * Launches the zxing-android-embedded full-screen scanner Activity.
 * Same approach as official Beam wallet (CaptureManager in a dedicated Activity).
 *
 * Call this composable from a screen — it immediately launches the scanner
 * and calls onScanned/onBack when done.
 */
@Composable
fun QRScannerScreen(onScanned: (String) -> Unit, onBack: () -> Unit) {
    val launched = remember { mutableStateOf(false) }
    val qrPrompt = stringResource(R.string.qr_scanner_prompt)

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw != null && raw.length > 10) {
            val address = parseBeamQr(raw)
            Log.d(TAG, "QR scanned: ${address.take(40)}...")
            onScanned(address)
        } else {
            // User cancelled or scan failed
            onBack()
        }
    }

    // Launch scanner Activity once on composition
    LaunchedEffect(Unit) {
        if (!launched.value) {
            launched.value = true
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(qrPrompt)
                setBeepEnabled(false)
                setOrientationLocked(true)
                setCaptureActivity(PortraitCaptureActivity::class.java)
                setCameraId(0) // Back camera
            }
            scanLauncher.launch(options)
        }
    }
}
