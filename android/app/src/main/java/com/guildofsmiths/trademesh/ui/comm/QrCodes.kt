package com.guildofsmiths.trademesh.ui.comm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import java.util.concurrent.Executors

/** A SmithNet id encoded as a scannable URI so a scan is unambiguous. */
fun smithNetIdUri(publicId: String): String = "smithnet:${publicId.uppercase()}"

/** Pull a bare 8-char id out of a scanned payload (smithnet:<id> or raw). */
fun parseScannedId(raw: String): String? {
    val s = raw.trim()
    val candidate = if (s.startsWith("smithnet:", ignoreCase = true)) s.substring(9) else s
    val cleaned = candidate.filter { it.isLetterOrDigit() }.uppercase()
    return if (cleaned.length == 8) cleaned else null
}

/** Generate a QR bitmap for [content] using zxing core (no UI dependency). */
fun qrBitmap(content: String, size: Int = 480): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val dark = 0xFF2A2520.toInt()
    val light = 0xFFFAFAF8.toInt()
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) dark else light)
        }
    }
    return bmp
}

/** Shows the user's own id as a QR for in-person swaps. */
@Composable
fun MyIdQrCard(publicId: String, modifier: Modifier = Modifier) {
    val bitmap = remember(publicId) { runCatching { qrBitmap(smithNetIdUri(publicId)) }.getOrNull() }
    Column(
        modifier = modifier
            .background(ConsoleTheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Your SmithNet id QR",
                modifier = Modifier.size(180.dp)
            )
        }
        Text("scan to message me", style = ConsoleTheme.commTimestamp, textAlign = TextAlign.Center)
    }
}

/**
 * Camera scanner for another user's id QR. Calls [onId] with the parsed 8-char
 * id (once), or [onCancel]. Requires CAMERA permission (requested inline).
 */
@Composable
fun ScanIdScreen(onId: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted; if (!granted) onCancel() }

    DisposableEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(ConsoleTheme.background), contentAlignment = Alignment.Center) {
            Text("Camera permission needed to scan.", style = ConsoleTheme.commBody)
        }
        return
    }

    var handled by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { proxy: ImageProxy ->
                        @Suppress("UnsafeOptInUsageError")
                        val mediaImage = proxy.image
                        if (mediaImage == null || handled) { proxy.close(); return@setAnalyzer }
                        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                        scanner.process(input)
                            .addOnSuccessListener { barcodes ->
                                for (b in barcodes) {
                                    val id = b.rawValue?.let { parseScannedId(it) }
                                    if (id != null && !handled) {
                                        handled = true
                                        onId(id)
                                        break
                                    }
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
        Box(
            Modifier.fillMaxWidth().align(Alignment.TopStart).padding(16.dp)
        ) {
            Text(
                "[cancel]",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .background(ConsoleTheme.surface)
                    .clickable { onCancel() }
                    .padding(8.dp)
            )
        }
    }
}
