package com.drakosanctis.auriga.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Auriga Camera Engine
 *
 * Built fresh in Kotlin using CameraX, taking inspiration from the existing
 * AURIGA v1 CameraService.java (continuous autofocus, no manual shutter,
 * always-on background processing) but modernized for broader device
 * compatibility:
 *
 * - CameraX abstracts away per-OEM Camera2 quirks (the exact problem v1's
 *   raw Camera2 implementation was vulnerable to across different phone
 *   models / Android skins).
 * - ImageAnalysis use case runs continuously and automatically — there is
 *   deliberately no capture button. Frames are pulled and analyzed as fast
 *   as the analyzer can keep up (STRATEGY_KEEP_ONLY_LATEST drops frames
 *   instead of queueing, so the pipeline never falls behind real time).
 * - Designed to later extend to external camera sources (USB/webcam) for
 *   the stated future desktop-camera-module scope, since CameraX's
 *   ImageAnalysis.Analyzer interface is decoupled from the capture source.
 */
class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "AurigaCameraEngine"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var onFrameAnalyzed: ((ImageProxy) -> Unit)? = null

    /**
     * Starts the continuous camera pipeline bound to the given preview surface.
     * No user action is required after this call — frames flow automatically
     * for the lifetime of [lifecycleOwner].
     */
    fun start(
        previewView: PreviewView,
        onFrame: (ImageProxy) -> Unit
    ) {
        onFrameAnalyzed = onFrame
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindUseCases(provider, previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(provider: ProcessCameraProvider, previewView: PreviewView) {
        provider.unbindAll()

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
                onFrameAnalyzed?.invoke(imageProxy)
            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis callback threw", e)
            } finally {
                // Always close, even on failure — leaking ImageProxy stalls the pipeline.
                imageProxy.close()
            }
        }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /** Toggles torch for low-light conditions, mirroring v1's auto-flashlight debounce logic. */
    fun setTorchEnabled(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun hasFlashUnit(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    fun stop() {
        cameraProvider?.unbindAll()
    }

    fun shutdown() {
        stop()
        analysisExecutor.shutdown()
    }
}
