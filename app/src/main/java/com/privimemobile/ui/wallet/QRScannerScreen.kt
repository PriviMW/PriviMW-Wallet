package com.privimemobile.ui.wallet

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QRScanner"

@Composable
fun QRScannerScreen(onScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    // AtomicBoolean — thread-safe, prevents duplicate scan callbacks
    val scannedRef = remember { AtomicBoolean(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera permission is required to scan QR codes", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xB3000000))) {
                    Text("Go Back", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            // zxing-android-embedded DecoratedBarcodeView — same scanner as official Beam wallet
            val barcodeView = remember { mutableStateOf<DecoratedBarcodeView?>(null) }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = DecoratedBarcodeView(ctx)
                    // Configure for QR codes only
                    view.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                    // Hide the default status text
                    view.setStatusText("")

                    view.decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            val value = result?.text ?: return
                            if (value.length > 10 && scannedRef.compareAndSet(false, true)) {
                                Log.d(TAG, "QR scanned: ${value.take(40)}...")
                                onScanned(value)
                            }
                        }
                        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
                    })

                    view.resume()
                    barcodeView.value = view
                    view
                },
            )

            // Pause/resume with lifecycle
            DisposableEffect(Unit) {
                onDispose {
                    barcodeView.value?.pause()
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x99000000),
            ) {
                Text("Point camera at a Beam QR code", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }

            Button(
                onClick = onBack,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xB3000000)),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
            ) {
                Text("Cancel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
