package com.drakosanctis.auriga.hazard

import kotlin.math.max
import kotlin.math.min

/**
 * Auriga Hazard Scoring Engine
 *
 * Ported from auriga-hazard-engine/scoring.ts (TypeScript) -> Kotlin.
 *
 * Computes hazardSeverity, hazardProbability, hazardConfidence, and riskScore
 * with explainable formulas documented inline.
 *
 * Constitutional constraints (carried over from the original repo):
 * - Never hide uncertainty
 * - Always expose confidence and risk
 * - Never silently discard low-confidence detections
 */

enum class SensorCondition {
    NORMAL, POOR_LIGHTING, MOTION_BLUR, SENSOR_NOISE
}

data class ScoringInput(
    val hazardClass: String,
    val rawConfidence: Float,
    val distanceMeters: Float? = null,
    val velocityMs: Float? = null,
    val isMobile: Boolean,
    val requiresImmediateAttention: Boolean,
    val sensorCondition: SensorCondition? = null,
    /** 0.0-0.15 positive certainty boost from place memory */
    val placeMemoryBoost: Float = 0f
)

data class ScoringExplanation(
    val whyDetected: String,
    val whyRiskScore: String,
    val whySeverity: String,
    /** Set externally by the prioritization step after ranking against other hazards. */
    val whyPrioritized: String = ""
)

data class ScoringResult(
    val hazardSeverity: Float,
    val hazardProbability: Float,
    val hazardConfidence: Float,
    val riskScore: Float,
    val formula: String,
    val explanation: ScoringExplanation
)

object HazardScoringEngine {

    /**
     * Calibrate raw detector confidence to match reliability expectations.
     *
     * calibrated = raw * sensorPenalty * (1 + placeMemoryBoost), clamped to [0, 1]
     *
     * Sensor penalties:
     *   NORMAL        -> 1.0  (no penalty)
     *   POOR_LIGHTING -> 0.80
     *   MOTION_BLUR   -> 0.75
     *   SENSOR_NOISE  -> 0.70
     *   null/unknown  -> 0.85 (conservative)
     */
    private fun calibrateConfidence(
        rawConfidence: Float,
        sensorCondition: SensorCondition?,
        placeMemoryBoost: Float
    ): Float {
        val penalty = when (sensorCondition) {
            SensorCondition.NORMAL -> 1.0f
            SensorCondition.POOR_LIGHTING -> 0.8f
            SensorCondition.MOTION_BLUR -> 0.75f
            SensorCondition.SENSOR_NOISE -> 0.7f
            null -> 0.85f
        }
        val calibrated = rawConfidence * penalty * (1f + placeMemoryBoost)
        return min(1f, max(0f, calibrated))
    }

    /**
     * Hazard severity — how bad the hazard would be if a collision occurs.
     *
     * severity = clamp(baseSeverity + distanceFactor + velocityFactor + attentionBonus, 0, 1)
     *
     * distanceFactor: <=0.5m -> +0.20, <=1m -> +0.10, <=2m -> +0.05, else 0
     * velocityFactor: >3 m/s -> +0.15, >1 m/s -> +0.08, else 0
     * attentionBonus: requiresImmediateAttention -> +0.10
     */
    private fun computeSeverity(input: ScoringInput): Pair<Float, String> {
        val base = HazardTaxonomy.getDefaultSeverityBase(input.hazardClass)

        val distanceFactor = input.distanceMeters?.let { d ->
            when {
                d <= 0.5f -> 0.2f
                d <= 1.0f -> 0.1f
                d <= 2.0f -> 0.05f
                else -> 0f
            }
        } ?: 0f

        val velocityFactor = input.velocityMs?.let { v ->
            when {
                v > 3f -> 0.15f
                v > 1f -> 0.08f
                else -> 0f
            }
        } ?: 0f

        val attentionBonus = if (input.requiresImmediateAttention) 0.1f else 0f

        val severity = min(1f, base + distanceFactor + velocityFactor + attentionBonus)

        val formula = buildString {
            append("baseSeverity(${input.hazardClass})=${"%.2f".format(base)}")
            input.distanceMeters?.let { append(" + distanceFactor(${"%.1f".format(it)}m)=${"%.2f".format(distanceFactor)}") }
            input.velocityMs?.let { append(" + velocityFactor(${"%.1f".format(it)}m/s)=${"%.2f".format(velocityFactor)}") }
            if (attentionBonus > 0f) append(" + attentionBonus=${"%.2f".format(attentionBonus)}")
            append(" = ${"%.3f".format(severity)}")
        }

        return severity to formula
    }

    /**
     * Hazard probability — how likely this hazard will cause impact.
     *
     * proximityFactor = 1 - (distance / MAX_SAFE_DISTANCE), clamped >= 0; MAX_SAFE_DISTANCE = 5m
     * Unknown distance -> assume worst-case proximityFactor = 0.8
     * mobilityFactor = isMobile ? 1.2 : 1.0
     * probability = clamp(calibratedConfidence * proximityFactor * mobilityFactor, 0, 1)
     */
    private fun computeProbability(
        calibratedConfidence: Float,
        distanceMeters: Float?,
        isMobile: Boolean
    ): Pair<Float, String> {
        val maxSafeDistance = 5f

        val proximityFactor: Float
        val proximityNote: String
        if (distanceMeters != null) {
            proximityFactor = max(0f, 1f - distanceMeters / maxSafeDistance)
            proximityNote = "proximityFactor(${"%.1f".format(distanceMeters)}m)=${"%.2f".format(proximityFactor)}"
        } else {
            proximityFactor = 0.8f
            proximityNote = "proximityFactor(unknown distance->assumed worst-case)=0.80"
        }

        val mobilityFactor = if (isMobile) 1.2f else 1.0f
        val probability = min(1f, calibratedConfidence * proximityFactor * mobilityFactor)

        val formula = "calibratedConfidence=${"%.2f".format(calibratedConfidence)} x $proximityNote x " +
            "mobilityFactor(${if (isMobile) "mobile" else "static"})=${"%.1f".format(mobilityFactor)} = ${"%.3f".format(probability)}"

        return probability to formula
    }

    /**
     * Composite risk score — weighted blend prioritizing severity, then proximity-driven
     * probability, then confidence (we never suppress low-confidence detections).
     *
     * riskScore = 0.45 x severity + 0.35 x probability + 0.20 x confidence
     */
    private fun computeRiskScore(severity: Float, probability: Float, confidence: Float): Float =
        min(1f, 0.45f * severity + 0.35f * probability + 0.2f * confidence)

    /** Main scoring function. Returns all scores with full explanation, for TTS/logging use. */
    fun scoreHazard(input: ScoringInput): ScoringResult {
        val hazardConfidence = calibrateConfidence(
            input.rawConfidence,
            input.sensorCondition,
            input.placeMemoryBoost
        )

        val (hazardSeverity, severityFormula) = computeSeverity(input)
        val (hazardProbability, probabilityFormula) = computeProbability(
            hazardConfidence,
            input.distanceMeters,
            input.isMobile
        )
        val riskScore = computeRiskScore(hazardSeverity, hazardProbability, hazardConfidence)

        val riskFormula = "riskScore = 0.45 x severity(${"%.2f".format(hazardSeverity)}) + " +
            "0.35 x probability(${"%.2f".format(hazardProbability)}) + " +
            "0.20 x confidence(${"%.2f".format(hazardConfidence)}) = ${"%.3f".format(riskScore)}"

        val whyDetected = buildString {
            append("Detected as \"${input.hazardClass}\" with raw confidence ${(input.rawConfidence * 100).toInt()}%")
            if (input.sensorCondition != null && input.sensorCondition != SensorCondition.NORMAL) {
                append(". Sensor condition: ${input.sensorCondition} (confidence penalized).")
            } else {
                append(".")
            }
            if (input.placeMemoryBoost > 0f) {
                append(" Place memory provided +${(input.placeMemoryBoost * 100).toInt()}% confidence boost.")
            }
        }

        val whySeverity = "Severity ${"%.2f".format(hazardSeverity)}: $severityFormula. " +
            if (input.requiresImmediateAttention)
                "Class marked as requiring immediate attention."
            else
                "Class does not require immediate attention by default."

        val whyRiskScore = "Risk ${"%.2f".format(riskScore)}: $riskFormula. Probability formula: $probabilityFormula."

        return ScoringResult(
            hazardSeverity = hazardSeverity,
            hazardProbability = hazardProbability,
            hazardConfidence = hazardConfidence,
            riskScore = riskScore,
            formula = riskFormula,
            explanation = ScoringExplanation(
                whyDetected = whyDetected,
                whyRiskScore = whyRiskScore,
                whySeverity = whySeverity
            )
        )
    }
}
