package com.sensora.app.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCase
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

val CAMERA_PERMISSION = arrayOf(Manifest.permission.CAMERA)

/**
 * Back-camera preview bound to the screen's lifecycle: the camera is released as soon as the
 * screen stops, so it never runs in the background.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    selector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    imageCapture: ImageCapture? = null,
    analysis: ImageAnalysis? = null,
    onReady: (Camera, PreviewView) -> Unit = { _, _ -> },
    onError: (Throwable) -> Unit = {},
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(ctx).apply {
            // TextureView-based so colour filters can be applied to the preview.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(owner, selector, imageCapture, analysis) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                val useCases: List<UseCase> = listOfNotNull(preview, imageCapture, analysis)
                val camera = p.bindToLifecycle(owner, selector, *useCases.toTypedArray())
                onReady(camera, previewView)
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { provider?.unbindAll() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Software brightness gain and optional green monochrome view, applied to the on-screen preview only. */
fun applyPreviewFilter(view: View, gain: Float, green: Boolean) {
    if (gain <= 1.001f && !green) {
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null) else view.setLayerType(View.LAYER_TYPE_NONE, null)
        return
    }
    val g = gain
    val m = if (green) {
        val r = 0.299f * g; val gg = 0.587f * g; val b = 0.114f * g
        floatArrayOf(
            0.25f * r, 0.25f * gg, 0.25f * b, 0f, 0f,
            r, gg, b, 0f, 0f,
            0.25f * r, 0.25f * gg, 0.25f * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    } else {
        floatArrayOf(g, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    }
    val filter = ColorMatrixColorFilter(ColorMatrix(m))
    if (Build.VERSION.SDK_INT >= 31) {
        view.setRenderEffect(RenderEffect.createColorFilterEffect(filter))
    } else {
        view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = filter })
    }
}

/** Saves a photo to Pictures/Sensora (Android 10+) or the app's picture folder (older versions; no permission needed). */
fun takePhoto(ctx: Context, capture: ImageCapture, onDone: (String?) -> Unit) {
    val name = "Sensora_${System.currentTimeMillis()}"
    val options = if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Sensora")
        }
        ImageCapture.OutputFileOptions.Builder(ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
    } else {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir
        ImageCapture.OutputFileOptions.Builder(File(dir, "$name.jpg")).build()
    }
    capture.takePicture(options, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            onDone(if (Build.VERSION.SDK_INT >= 29) "Pictures/Sensora/$name.jpg" else output.savedUri?.path ?: name)
        }
        override fun onError(exception: ImageCaptureException) = onDone(null)
    })
}
