package com.drakosanctis.auriga.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Auriga Virtual Fiducial Engine — Geometric Distance Estimation
 *
 * Ported from auriga-virtual-fiducials (TypeScript) -> Kotlin for on-device use.
 *
 * Implements the pinhole camera model for monocular distance estimation:
 *
 *   D = (S_mm * f_px) / W_px / 1000
 *
 * Where:
 *   D      = estimated distance in metres
 *   S_mm   = physical marker size in millimetres
 *   f_px   = focal length in pixels
 *   W_px   = detected marker width in pixels
 *
 * Confidence scoring accounts for:
 *   - Aspect ratio consistency (when height is available)
 *   - Marker size relative to sensor (very small or very large markers degrade confidence)
 *   - Lighting condition penalties
 *   - Partial occlusion penalties
 */

enum class LightingCondition {
    NORMAL,
    LOW_LIGHT,
    BRIGHT,
    REFLECTIVE
}

data class FiducialEstimationInput(
    val focalLengthPx: Float,
    val markerSizeMm: Float,
    val sensorWidthPx: Float,
    val sensorHeightPx: Float,
    val markerPixelWidth: Float,
    val markerPixelHeight: Float? = null,
    val lightingCondition: LightingCondition? = null,
    val partialOcclusion: Boolean = false
)

data class FiducialEstimationResult(
    val estimatedDistanceM: Float,
    val confidenceScore: Float,
    val uncertaintyM: Float,
    val confidenceLowerM: Float,
    val confidenceUpperM: Float
)

object VirtualFiducialEngine {

    /**
     * Pinhole camera model: D = (S_mm * f_px) / W_px / 1000
     *
     * Returns null distance components if markerPixelWidth <= 0 (avoids divide-by-zero / NaN).
     */
    fun estimateDistance(input: FiducialEstimationInput): FiducialEstimationResult {
        require(input.markerPixelWidth > 0f) { "markerPixelWidth must be > 0" }

        val estimatedDistanceM =
            (input.markerSizeMm * input.focalLengthPx) / input.markerPixelWidth / 1000f

        var confidence = 1.0f

        // Aspect ratio check — square markers should have consistent pixel dimensions
        val height = input.markerPixelHeight
        if (height != null && height > 0f) {
            val aspectRatio = input.markerPixelWidth / height
            val aspectPenalty = abs(1.0f - aspectRatio) * 0.3f
            confidence -= min(aspectPenalty, 0.3f)
        }

        // Marker size relative to sensor width — tiny markers are noisy, huge markers suggest clipping
        val markerFraction = input.markerPixelWidth / input.sensorWidthPx
        confidence -= when {
            markerFraction < 0.02f -> 0.25f // very small marker — high pixel-level noise
            markerFraction < 0.05f -> 0.10f
            markerFraction > 0.8f -> 0.15f // marker fills most of frame — likely very close / clipping
            else -> 0f
        }

        // Lighting condition penalties
        confidence -= when (input.lightingCondition) {
            LightingCondition.LOW_LIGHT -> 0.20f
            LightingCondition.REFLECTIVE -> 0.25f
            LightingCondition.BRIGHT -> 0.05f
            else -> 0f
        }

        // Partial occlusion penalty
        if (input.partialOcclusion) {
            confidence -= 0.30f
        }

        // Clamp confidence to [0.05, 1.0]
        confidence = max(0.05f, min(1.0f, confidence))

        // Uncertainty scales inversely with confidence.
        // Base uncertainty: +/-5% of estimated distance, inflated by low confidence.
        val baseUncertaintyFraction = 0.05f
        val confidenceInflation = 1f + (1f - confidence) * 2f
        val uncertaintyM = estimatedDistanceM * baseUncertaintyFraction * confidenceInflation

        return FiducialEstimationResult(
            estimatedDistanceM = round4(estimatedDistanceM),
            confidenceScore = round3(confidence),
            uncertaintyM = round4(uncertaintyM),
            confidenceLowerM = round4(max(0f, estimatedDistanceM - uncertaintyM)),
            confidenceUpperM = round4(estimatedDistanceM + uncertaintyM)
        )
    }

    /** Evaluation metrics — MAE, RMSE, MAPE. Useful for on-device self-calibration/logging. */
    data class ErrorMetrics(
        val mae: Float,
        val rmse: Float,
        val mape: Float,
        val minErrorM: Float,
        val maxErrorM: Float,
        val meanEstimatedDistanceM: Float,
        val meanMeasuredDistanceM: Float
    )

    fun computeMetrics(pairs: List<Pair<Float, Float>>): ErrorMetrics {
        if (pairs.isEmpty()) {
            return ErrorMetrics(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }

        val absErrors = pairs.map { (estimated, measured) -> abs(estimated - measured) }
        val squaredErrors = pairs.map { (estimated, measured) -> (estimated - measured).pow(2) }
        val pctErrors = pairs.map { (estimated, measured) ->
            if (measured == 0f) 0f else abs((estimated - measured) / measured) * 100f
        }

        val mae = absErrors.sum() / pairs.size
        val rmse = sqrt(squaredErrors.sum() / pairs.size)
        val mape = pctErrors.sum() / pairs.size
        val minErrorM = absErrors.min()
        val maxErrorM = absErrors.max()
        val meanEstimatedDistanceM = pairs.sumOf { it.first.toDouble() }.toFloat() / pairs.size
        val meanMeasuredDistanceM = pairs.sumOf { it.second.toDouble() }.toFloat() / pairs.size

        return ErrorMetrics(
            mae = round5(mae),
            rmse = round5(rmse),
            mape = round2(mape),
            minErrorM = round5(minErrorM),
            maxErrorM = round5(maxErrorM),
            meanEstimatedDistanceM = round4(meanEstimatedDistanceM),
            meanMeasuredDistanceM = round4(meanMeasuredDistanceM)
        )
    }

    private fun round2(v: Float) = (round(v * 100) / 100)
    private fun round3(v: Float) = (round(v * 1000) / 1000)
    private fun round4(v: Float) = (round(v * 10000) / 10000)
    private fun round5(v: Float) = (round(v * 100000) / 100000)
}
