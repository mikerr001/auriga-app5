package com.drakosanctis.auriga.hazard

/**
 * Auriga Dynamic Hazard Prediction Engine
 *
 * Ported from auriga-hazard-engine/prediction.ts (TypeScript) -> Kotlin.
 *
 * Estimates time-to-collision and risk adjustment for moving hazards —
 * this is the "is the vehicle/person on a direct collision course" logic.
 *
 * Constitutional constraint:
 * - Always expose uncertainty estimates
 * - Never suppress uncertain predictions — expose them with an uncertainty radius
 */

data class PredictionInput(
    val hazardClass: String,
    val isMobile: Boolean,
    val distanceMeters: Float? = null,
    val velocityMs: Float? = null,
    val direction: String
)

data class PredictionResult(
    val predictedTimeToCollisionSeconds: Float?,
    /** Add this to riskScore: -0.2 to +0.2 */
    val collisionRiskAdjustment: Float,
    val uncertaintyRadiusMeters: Float?,
    val description: String
)

object HazardPredictionEngine {

    private const val UNCERTAINTY_FACTOR = 0.3f

    /**
     * Predict time-to-collision (TTC) and risk adjustment for dynamic hazards.
     *
     * If hazard is mobile, has a known velocity, and a known distance:
     *   ttc = distanceMeters / velocityMs
     *   adjustment = +0.15 if ttc < 3s, +0.08 if ttc < 6s, else +0.02
     * If static, or velocity/distance unknown:
     *   no prediction needed, adjustment = 0
     *
     * uncertaintyRadius = velocityMs * UNCERTAINTY_FACTOR (assumes 30% velocity uncertainty)
     */
    fun predict(input: PredictionInput): PredictionResult {
        val velocity = input.velocityMs
        val distance = input.distanceMeters

        if (!input.isMobile || velocity == null || distance == null) {
            return PredictionResult(
                predictedTimeToCollisionSeconds = null,
                collisionRiskAdjustment = 0f,
                uncertaintyRadiusMeters = null,
                description = "Static hazard — no trajectory prediction required."
            )
        }

        val ttc = if (velocity > 0f) distance / velocity else null
        val uncertaintyRadius = velocity * UNCERTAINTY_FACTOR

        val adjustment: Float
        val description: String

        when {
            ttc == null -> {
                adjustment = 0f
                description = "${input.hazardClass} velocity is zero — not approaching."
            }
            ttc < 3f -> {
                adjustment = 0.15f
                description = "${input.hazardClass} approaching at ${"%.1f".format(velocity)} m/s — " +
                    "predicted contact in ${"%.1f".format(ttc)}s. IMMEDIATE EVASION REQUIRED. " +
                    "Uncertainty: +/-${"%.1f".format(uncertaintyRadius)}m."
            }
            ttc < 6f -> {
                adjustment = 0.08f
                description = "${input.hazardClass} approaching at ${"%.1f".format(velocity)} m/s — " +
                    "predicted contact in ${"%.1f".format(ttc)}s. Prepare to navigate. " +
                    "Uncertainty: +/-${"%.1f".format(uncertaintyRadius)}m."
            }
            else -> {
                adjustment = 0.02f
                description = "${input.hazardClass} moving at ${"%.1f".format(velocity)} m/s — " +
                    "contact in ${"%.1f".format(ttc)}s (low urgency). " +
                    "Uncertainty: +/-${"%.1f".format(uncertaintyRadius)}m."
            }
        }

        return PredictionResult(
            predictedTimeToCollisionSeconds = ttc,
            collisionRiskAdjustment = adjustment,
            uncertaintyRadiusMeters = uncertaintyRadius,
            description = description
        )
    }
}
