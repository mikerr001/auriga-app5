package com.drakosanctis.auriga.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult

/**
 * Auriga Object Detection Engine
 *
 * Wraps MediaPipe Tasks Vision's ObjectDetector rather than a raw TFLite
 * Interpreter + YOLO post-processing pipeline (which is what AURIGA v1 used
 * via YoloDetector.java).
 *
 * Why MediaPipe Tasks Vision over raw YOLO/TFLite, per the camera/vision
 * pipeline recommendation:
 * - Handles CPU/GPU/NNAPI delegate selection per-device automatically,
 *   which matters across the wide range of phone models in the target
 *   markets (low-end to mid-range Android dominant in South Asia / Africa).
 * - The .tflite model file itself is interchangeable — start with a
 *   lightweight detector (e.g. EfficientDet-Lite0) and swap in a custom
 *   model later without rewriting the inference plumbing.
 * - Same Tasks Vision API family exists for desktop/Python/C++ runtimes,
 *   giving a clearer future path to webcam/desktop support than an
 *   Android-only YOLO wrapper would.
 *
 * Model file: expects a .tflite object detector model at
 * assets/models/object_detector.tflite. This is NOT bundled in this
 * repository — drop a compatible EfficientDet-Lite or MobileNet-SSD
 * .tflite model into app/src/main/assets/models/ before building, or wire
 * up a download step in the build pipeline.
 */

data class DetectedObject(
    val label: String,
    val confidence: Float,
    /** Bounding box in pixel coordinates of the analyzed frame. */
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
    val frameWidth: Int,
    val frameHeight: Int
) {
    val centerX: Float get() = (boundingBoxLeft + boundingBoxRight) / 2f
    val centerY: Float get() = (boundingBoxTop + boundingBoxBottom) / 2f
    val pixelWidth: Float get() = boundingBoxRight - boundingBoxLeft
    val pixelHeight: Float get() = boundingBoxBottom - boundingBoxTop
}

class ObjectDetectionEngine(private val context: Context) {

    companion object {
        private const val TAG = "AurigaObjectDetection"
        private const val MODEL_ASSET_PATH = "models/object_detector.tflite"
        private const val MAX_RESULTS = 8
        private const val SCORE_THRESHOLD = 0.45f
    }

    private var detector: ObjectDetector? = null
    private var isInitialized = false
    private var lastInitError: String? = null

    /**
     * Initializes the detector. Tries GPU delegate first for speed, falls
     * back to CPU if GPU delegate init fails (common on lower-end/older
     * devices) — this fallback chain matters specifically for the budget
     * Android phones dominant in the target markets.
     */
    fun initialize(): Boolean {
        if (isInitialized) return true

        val attempts = listOf(Delegate.GPU, Delegate.CPU)
        for (delegate in attempts) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .setDelegate(delegate)
                    .build()

                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(MAX_RESULTS)
                    .setScoreThreshold(SCORE_THRESHOLD)
                    .build()

                detector = ObjectDetector.createFromOptions(context, options)
                isInitialized = true
                Log.i(TAG, "ObjectDetector initialized with delegate=$delegate")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "ObjectDetector init failed with delegate=$delegate", e)
                lastInitError = e.message
            }
        }

        Log.e(TAG, "ObjectDetector failed to initialize on all delegates. Last error: $lastInitError")
        return false
    }

    fun lastError(): String? = lastInitError

    /**
     * Runs detection on a single bitmap frame. Returns an empty list (never
     * null/throws to the caller) if the detector isn't initialized or
     * detection fails — the continuous camera loop should keep running even
     * if a single frame's detection errors out.
     */
    fun detect(bitmap: Bitmap): List<DetectedObject> {
        val activeDetector = detector ?: return emptyList()

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: ObjectDetectionResult = activeDetector.detect(mpImage)

            result.detections().mapNotNull { detection ->
                val category = detection.categories().firstOrNull() ?: return@mapNotNull null
                val box = detection.boundingBox()
                DetectedObject(
                    label = category.categoryName() ?: "unknown_object",
                    confidence = category.score(),
                    boundingBoxLeft = box.left,
                    boundingBoxTop = box.top,
                    boundingBoxRight = box.right,
                    boundingBoxBottom = box.bottom,
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed on frame", e)
            emptyList()
        }
    }

    fun close() {
        detector?.close()
        detector = null
        isInitialized = false
    }
}
