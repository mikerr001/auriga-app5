package com.drakosanctis.auriga.core

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.drakosanctis.auriga.audio.AudioEngine
import com.drakosanctis.auriga.bearing.BearingEngine
import com.drakosanctis.auriga.camera.CameraCalibrationManager
import com.drakosanctis.auriga.camera.CameraEngine
import com.drakosanctis.auriga.geometry.FiducialEstimationInput
import com.drakosanctis.auriga.geometry.LightingCondition
import com.drakosanctis.auriga.geometry.VirtualFiducialEngine
import com.drakosanctis.auriga.guidance.GuidanceEngine
import com.drakosanctis.auriga.haptic.HapticEngine
import com.drakosanctis.auriga.hazard.HazardPredictionEngine
import com.drakosanctis.auriga.hazard.HazardScoringEngine
import com.drakosanctis.auriga.hazard.HazardTaxonomy
import com.drakosanctis.auriga.hazard.PredictionInput
import com.drakosanctis.auriga.hazard.ScoringInput
import com.drakosanctis.auriga.hazard.SensorCondition
import com.drakosanctis.auriga.memory.AurigaDatabase
import com.drakosanctis.auriga.memory.ContinuousLearningRepository
import com.drakosanctis.auriga.memory.PlaceMemoryRepository
import com.drakosanctis.auriga.memory.WorldModelLite
import com.drakosanctis.auriga.mind.MindEngine
import com.drakosanctis.auriga.mind.ModelDownloadManager
import com.drakosanctis.auriga.vision.DetectedObject
import com.drakosanctis.auriga.vision.ImageConversion
import com.drakosanctis.auriga.vision.ObjectDetectionEngine
import com.drakosanctis.auriga.vision.ObjectTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Auriga Pipeline
 *
 * The central orchestrator: Camera -> Object Detection -> Virtual Fiducial
 * distance -> Bearing -> Hazard scoring/prediction -> Guidance -> Audio/Haptic.
 *
 * This is the MVP-v1 capability chain exactly as specified in the
 * development-order roadmap (Week 1 Camera/Detection through Week 6 Haptics),
 * implemented as one continuously-running pipeline rather than discrete
 * weekly milestones, since this is a single integrated build rather than a
 * staged rollout.
 *
 * Known-object reference widths are used for the Virtual Fiducial distance
 * estimate's marker-size input, since MVP v1 has no calibrated physical
 * fiducial markers — it estimates physical size per detected class using
 * typical real-world dimensions. This is an approximation; accuracy depends
 * on how close a given real object is to its class's typical size, and
 * should be validated against the <15% error target from the research phase
 * once real-world testing is possible.
 */
class AurigaPipeline(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onStatusUpdate: (String) -> Unit
) {
    companion object {
        private const val TAG = "AurigaPipeline"

        // Approximate real-world widths (mm) used as the Virtual Fiducial
        // engine's marker-size input per detected class. Rough estimates —
        // refine with real measurement data as the research phase progresses.
        private val TYPICAL_WIDTHS_MM: Map<String, Float> = mapOf(
            "person" to 450f,
            "chair" to 450f,
            "door" to 800f,
            "table" to 1000f,
            "car" to 1800f,
            "bicycle" to 600f,
            "motorcycle" to 800f,
            "couch" to 1600f,
            "potted plant" to 300f,
            "dining table" to 1200f
        )
        private const val DEFAULT_WIDTH_MM = 500f
    }

    private val cameraEngine = CameraEngine(context, lifecycleOwner)
    private val detectionEngine = ObjectDetectionEngine(context)
    private val audioEngine = AudioEngine(context)
    private val hapticEngine = HapticEngine(context)
    private val worldModel = WorldModelLite()
    private val calibrationManager = CameraCalibrationManager(context)
    private val objectTracker = ObjectTracker()

    private val database = AurigaDatabase.getInstance(context)
    private val placeMemory = PlaceMemoryRepository(database.placeMemoryDao())
    private val continuousLearning = ContinuousLearningRepository(database.continuousLearningDao())

    private val modelDownloadManager = ModelDownloadManager(context)
    private val mindEngine = MindEngine(context, modelDownloadManager.modelFile())

    private val pipelineScope = CoroutineScope(Dispatchers.Default)

    private var currentPlaceId: Long? = null
    private var isReady = false

    // Resolved once the first frame's dimensions are known, since calibration
    // depends on the actual analyzed frame width.
    private var calibration: com.drakosanctis.auriga.camera.CameraCalibrationProfile? = null

    fun start(previewView: PreviewView) {
        onStatusUpdate("Initializing detection engine…")

        val detectorOk = detectionEngine.initialize()
        if (!detectorOk) {
            val reason = detectionEngine.lastError() ?: "unknown error"
            onStatusUpdate("Detection engine failed to start: $reason")
            pipelineScope.launch {
                continuousLearning.recordFailure("object_detection_init", reason)
            }
            return
        }

        audioEngine.initialize { ttsOk ->
            if (!ttsOk) {
                Log.w(TAG, "TextToSpeech failed to initialize — guidance will be silent.")
            }
        }

        isReady = true
        onStatusUpdate("Auriga is watching.")

        cameraEngine.start(previewView) { imageProxy ->
            processFrame(imageProxy)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        if (!isReady) return

        val bitmap = ImageConversion.imageProxyToBitmap(imageProxy) ?: return
        val detections = detectionEngine.detect(bitmap)
        if (detections.isEmpty()) return

        // Resolve calibration once we know the real analyzed frame width.
        // getCalibration() is cheap after the first call (cached in-memory
        // via SharedPreferences-backed manager), so calling it per-frame is
        // fine, but we still only do the actual Camera2 read once per
        // resolution thanks to CameraCalibrationManager's own caching.
        val resolvedCalibration = calibrationManager.getCalibration(bitmap.width)
        calibration = resolvedCalibration

        // First pass: estimate distance for every detection so the tracker
        // can be updated with real-world distances, not just pixel positions.
        val withDistances = detections.map { detection ->
            val widthMm = TYPICAL_WIDTHS_MM[detection.label] ?: DEFAULT_WIDTH_MM
            val fiducialResult = VirtualFiducialEngine.estimateDistance(
                FiducialEstimationInput(
                    focalLengthPx = resolvedCalibration.focalLengthPx,
                    markerSizeMm = widthMm,
                    sensorWidthPx = detection.frameWidth.toFloat(),
                    sensorHeightPx = detection.frameHeight.toFloat(),
                    markerPixelWidth = detection.pixelWidth.coerceAtLeast(1f),
                    markerPixelHeight = detection.pixelHeight,
                    lightingCondition = LightingCondition.NORMAL
                )
            )
            detection to fiducialResult
        }

        val velocityByDetection = objectTracker.update(
            withDistances.map { (detection, fiducial) -> detection to fiducial.estimatedDistanceM }
        )

        // Process the single highest-confidence detection per frame for MVP v1
        // guidance purposes — multiple simultaneous spoken warnings would be
        // unusable. Lower-priority detections still feed the world model.
        val primary = withDistances.maxByOrNull { it.first.confidence }?.first

        for ((detection, fiducialResult) in withDistances) {
            val velocityMs = velocityByDetection[detection]
            evaluateDetection(
                detection = detection,
                fiducialResult = fiducialResult,
                velocityMs = velocityMs,
                calibration = resolvedCalibration,
                isPrimary = detection == primary
            )
        }
    }

    private fun evaluateDetection(
        detection: DetectedObject,
        fiducialResult: com.drakosanctis.auriga.geometry.FiducialEstimationResult,
        velocityMs: Float?,
        calibration: com.drakosanctis.auriga.camera.CameraCalibrationProfile,
        isPrimary: Boolean
    ) {
        val bearingDegrees = BearingEngine.bearingFromPixelOffset(
            objectCenterPx = detection.centerX,
            imageWidthPx = detection.frameWidth.toFloat(),
            horizontalFovDegrees = calibration.horizontalFovDegrees
        )
        val direction = BearingEngine.resolveDirection(bearingDegrees = bearingDegrees)

        val hazardClass = mapDetectionLabelToHazardClass(detection.label)
        val classDef = HazardTaxonomy.getClassDef(hazardClass)

        val scoring = HazardScoringEngine.scoreHazard(
            ScoringInput(
                hazardClass = hazardClass,
                rawConfidence = detection.confidence,
                distanceMeters = fiducialResult.estimatedDistanceM,
                // Velocity is now real (cross-frame tracked), not always null.
                // Hazard "approaching" semantics expect a positive magnitude
                // for speed — the tracker reports signed velocity (negative
                // = approaching), so HazardScoringEngine/PredictionEngine,
                // which were designed around an unsigned "how fast" input,
                // receive the absolute value while direction-of-travel
                // (approaching vs receding) is exposed separately below.
                velocityMs = velocityMs?.let { kotlin.math.abs(it) },
                isMobile = classDef?.isMobile ?: false,
                requiresImmediateAttention = classDef?.requiresImmediateAttention ?: false,
                sensorCondition = SensorCondition.NORMAL
            )
        )

        val isApproaching = velocityMs != null && velocityMs < 0f

        val prediction = HazardPredictionEngine.predict(
            PredictionInput(
                hazardClass = hazardClass,
                isMobile = classDef?.isMobile ?: false,
                distanceMeters = fiducialResult.estimatedDistanceM,
                // Time-to-collision is only meaningful when the object is
                // actually approaching — a receding object has no collision
                // course regardless of speed.
                velocityMs = if (isApproaching) kotlin.math.abs(velocityMs!!) else null,
                direction = direction.name
            )
        )

        worldModel.recordHazardObservation(hazardClass, fiducialResult.estimatedDistanceM)

        if (isPrimary) {
            val utterance = GuidanceEngine.describeHazard(
                hazardClass = hazardClass,
                humanLabel = detection.label.replace('_', ' '),
                distance = fiducialResult,
                direction = direction,
                scoring = scoring,
                ttcSeconds = prediction.predictedTimeToCollisionSeconds
            )
            audioEngine.enqueue(utterance)
            hapticEngine.playFromGuidanceCode(utterance.hapticPattern)
        }

        pipelineScope.launch {
            currentPlaceId?.let { placeId ->
                placeMemory.recordObjectObservation(placeId, hazardClass)
            }
        }
    }

    /**
     * Maps a raw object-detector class label to Auriga's hazard taxonomy.
     * The detector's label set depends on which .tflite model is bundled;
     * this mapping should be extended as the real model's label map is finalized.
     */
    private fun mapDetectionLabelToHazardClass(label: String): String = when (label.lowercase()) {
        "person" -> "pedestrian"
        "bicycle" -> "bicycle"
        "car", "motorcycle", "bus", "truck" -> "vehicle"
        "chair", "couch", "bench", "dining table", "table" -> "furniture"
        "door" -> "door"
        "potted plant" -> "furniture"
        else -> "unknown_object"
    }

    fun enterPlace(label: String) {
        pipelineScope.launch {
            val place = placeMemory.recordVisit(label)
            currentPlaceId = place.id
            worldModel.setCurrentPlace(label)
        }
    }

    /**
     * Handles an "Ask Auriga" voice query. Routes to MindEngine, respecting
     * the co-pilot-not-assistant priority: if AudioEngine currently has a
     * CRITICAL hazard utterance in flight, the mind response is deferred
     * briefly rather than competing for the speech channel — a navigation
     * hazard always wins over a conversational answer.
     *
     * [isOnline] should reflect actual current connectivity (caller's
     * responsibility — e.g. via ConnectivityManager), since MindEngine's
     * cloud fallback only makes sense when genuinely online.
     */
    fun askMind(prompt: String, isOnline: Boolean, onSpoken: (String) -> Unit) {
        pipelineScope.launch {
            if (audioEngine.hasCriticalUtteranceInFlight()) {
                // Brief wait for the critical warning to finish rather than
                // talking over it. A fixed short delay is sufficient for MVP
                // v1 rather than building a full callback-based queue join.
                kotlinx.coroutines.delay(1500L)
            }

            val deviceSupportsOnDevice = mindEngine.hasSufficientRam()

            // Only attempt to resolve (bundle-copy or download) the
            // on-device model if this device can actually run it. Skipping
            // this on low-RAM devices avoids wasted bandwidth/storage on a
            // model that could never be loaded anyway — those devices go
            // straight to cloud (if online) instead.
            if (deviceSupportsOnDevice) {
                // Resolves the model from a build-time-bundled asset (fast, no
                // network) if present, otherwise downloads it. Cheap no-op if
                // the model is already sitting in internal storage from a prior
                // call.
                val modelResolution = downloadMindModel { _, _ -> }
                if (modelResolution is com.drakosanctis.auriga.mind.DownloadResult.Failed && !isOnline) {
                    onSpoken(
                        "Auriga's voice assistant isn't ready yet and there's no internet " +
                            "connection to set it up. Core navigation is still active."
                    )
                    return@launch
                }
            } else if (!isOnline) {
                onSpoken(
                    "On-device voice assistant isn't supported on this phone's memory, " +
                        "and there's no internet connection right now for the online " +
                        "assistant. Core navigation is still fully active."
                )
                return@launch
            }

            val result = mindEngine.generate(
                prompt = prompt,
                preferOnDevice = deviceSupportsOnDevice,
                isOnline = isOnline
            )

            result.fold(
                onSuccess = { response ->
                    onSpoken(response.text)
                    audioEngine.enqueue(
                        com.drakosanctis.auriga.guidance.GuidanceUtterance(
                            text = response.text,
                            urgency = com.drakosanctis.auriga.guidance.UrgencyLevel.ELEVATED
                        )
                    )
                },
                onFailure = { error ->
                    val message = "I couldn't process that right now: ${error.message ?: "unknown error"}."
                    onSpoken(message)
                    audioEngine.enqueue(
                        com.drakosanctis.auriga.guidance.GuidanceUtterance(
                            text = message,
                            urgency = com.drakosanctis.auriga.guidance.UrgencyLevel.ROUTINE
                        )
                    )
                }
            )
        }
    }

    fun isMindModelReady(): Boolean = modelDownloadManager.isModelReady()

    /**
     * Ensures the on-device model is available, preferring a build-time
     * bundled asset (instant, no network) over a runtime download. Safe to
     * call even if the model is already in place — returns immediately in
     * that case. [onProgress] only fires meaningfully if an actual network
     * download is needed (bundled-asset resolution is fast enough that
     * progress reporting wouldn't add value).
     */
    suspend fun downloadMindModel(onProgress: (downloaded: Long, total: Long) -> Unit) =
        modelDownloadManager.ensureModelAvailable(onProgress)

    fun stop() {
        isReady = false
        cameraEngine.stop()
        audioEngine.shutdown()
        hapticEngine.stop()
        objectTracker.reset()
    }

    fun shutdown() {
        stop()
        cameraEngine.shutdown()
        detectionEngine.close()
        mindEngine.shutdown()
    }
}
