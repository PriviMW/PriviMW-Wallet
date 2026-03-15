package com.privimemobile.ui.wallet

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.privimemobile.ui.theme.C
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * QR code scanner using CameraX + ZXing.
 *
 * @param onScanned Called with the decoded QR string when a code is successfully read.
 * @param onBack Called when the user cancels or goes back.
 */
@Composable
fun QRScannerScreen(onScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var scanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!hasPermission) {
            // Permission denied state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Camera permission is required to scan QR codes",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xB3000000)
                    ),
                ) {
                    Text("Go Back", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            // Camera preview with QR analysis
            CameraPreview(
                onQrDetected = { value ->
                    if (!scanned && value.length > 10) {
                        scanned = true
                        onScanned(value)
                    }
                },
            )

            // Overlay hint
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x99000000),
            ) {
                Text(
                    "Point camera at a Beam QR code",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            // Cancel button
            Button(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xB3000000)
                ),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
            ) {
                Text("Cancel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onQrDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }}

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val buffer = imageProxy.planes[0].buffer
                            val data = ByteArray(buffer.remaining())
                            buffer.get(data)

                            val source = PlanarYUVLuminanceSource(
                                data,
                                imageProxy.width,
                                imageProxy.height,
                                0, 0,
                                imageProxy.width,
                                imageProxy.height,
                                false,
                            )

                            try {
                                val result = reader.decodeWithState(
                                    BinaryBitmap(HybridBinarizer(source))
                                )
                                result.text?.let { text ->
                                    onQrDetected(text)
                                }
                            } catch (_: NotFoundException) {
                                // No QR code found in this frame
                            } catch (_: Exception) {
                                // Other decode errors
                            } finally {
                                reader.reset()
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (_: Exception) {
                    // Camera binding failed
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}
