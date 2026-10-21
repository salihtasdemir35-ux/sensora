package com.sensora.app.ui.camera

import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.concurrent.futures.await
import com.sensora.app.R
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.common.*
import java.util.Locale

@Composable
fun LowLightScreen(tool: Tool, onBack: () -> Unit) {
    ToolScaffold(tool, onBack) { padding ->
        ScreenColumn(padding) {
            WarningCard(stringResource(R.string.lowlight_disclaimer))
            PermissionGate(CAMERA_PERMISSION, R.string.camera_rationale) { LowLightContent() }
        }
    }
}

@Composable
private fun LowLightContent() {
    val ctx = LocalContext.current
    var selector by remember { mutableStateOf<CameraSelector?>(null) }
    var nightExtension by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var gain by rememberSaveable { mutableStateOf(1f) }
    var green by rememberSaveable { mutableStateOf(false) }
    var torch by rememberSaveable { mutableStateOf(false) }
    var ev by rememberSaveable { mutableStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val savedMsg = stringResource(R.string.photo_saved)
    val failedMsg = stringResource(R.string.photo_failed)

    // Use the manufacturer's Night extension when the device offers one; otherwise the plain camera.
    LaunchedEffect(Unit) {
        selector = try {
            val provider = ProcessCameraProvider.getInstance(ctx).await()
            val mgr = ExtensionsManager.getInstanceAsync(ctx, provider).await()
            if (mgr.isExtensionAvailable(CameraSelector.DEFAULT_BACK_CAMERA, ExtensionMode.NIGHT)) {
                nightExtension = true
                mgr.getExtensionEnabledCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA, ExtensionMode.NIGHT)
            } else CameraSelector.DEFAULT_BACK_CAMERA
        } catch (e: Exception) {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    LaunchedEffect(gain, green, preview) { preview?.let { applyPreviewFilter(it, gain, green) } }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }
    LaunchedEffect(ev, camera) { camera?.cameraControl?.setExposureCompensationIndex(ev) }

    val sel = selector
    if (sel == null) { WaitingCard(); return }
    CameraPreview(
        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
        selector = sel,
        imageCapture = capture,
        onReady = { c, v -> camera = c; preview = v; error = false },
        onError = { error = true },
    )
    if (error) WarningCard(stringResource(R.string.camera_error), isError = true)
    InfoCard {
        Text(stringResource(if (nightExtension) R.string.lowlight_ext_on else R.string.lowlight_ext_off), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.lowlight_gain, String.format(Locale.getDefault(), "%.1f×", gain)))
        Slider(value = gain, onValueChange = { gain = it }, valueRange = 1f..4f)
        val exposure = camera?.cameraInfo?.exposureState
        if (exposure != null && exposure.isExposureCompensationSupported) {
            val range = exposure.exposureCompensationRange
            Text(stringResource(R.string.lowlight_exposure, ev.toString()))
            Slider(value = ev.toFloat(), onValueChange = { ev = it.toInt() },
                valueRange = range.lower.toFloat()..range.upper.toFloat(),
                steps = (range.upper - range.lower - 1).coerceAtLeast(0))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = green, onCheckedChange = { green = it })
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.lowlight_green))
        }
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = torch, onCheckedChange = { torch = it })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.lowlight_torch))
            }
        }
    }
    Button(onClick = {
        takePhoto(ctx, capture) { path ->
            Toast.makeText(ctx, if (path != null) "$savedMsg $path" else failedMsg, Toast.LENGTH_LONG).show()
        }
    }, modifier = Modifier.fillMaxWidth(), enabled = camera != null) { Text(stringResource(R.string.photo_take)) }
    Text(stringResource(R.string.lowlight_photo_note), style = MaterialTheme.typography.bodySmall)
}
