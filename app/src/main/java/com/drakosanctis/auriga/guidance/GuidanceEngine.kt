package com.drakosanctis.auriga.guidance

import com.drakosanctis.auriga.bearing.BearingEngine
import com.drakosanctis.auriga.bearing.Direction
import com.drakosanctis.auriga.geometry.FiducialEstimationResult
import com.drakosanctis.auriga.hazard.ScoringResult

/**
 * Auriga Guidance Engine
 *
 * Designed from the Auriga product deliberations (not ported from a repo —
 * auriga-guidance-engine was an empty scaffold with no implementation).
 *
 * Responsibilities, as specified across the Auriga design conversations:
 * - Convert raw detection + hazard + bearing output into short, natural,
 *   human-usable speech instructions.
 * - "Turn until beep" directional correction during active navigation.
 * - Periodic checkpoint-relative distance updates (~every 25% of remaining distance).
 * - Organic hazard warnings — urgent hazards interrupt; routine ones wait
 *   for a natural pause.
 * - Silent by default: Auriga only speaks when there's something worth saying,
 *   not a running narration of everything detected.
 */

enum class UrgencyLevel {
    /** Immediate danger — interrupts everything, spoken instantly. */
    CRITICAL,
    /** Worth mentioning soon, but can wait a beat if mid-sentence. */
    ELEVATED,
    /** Informational — queued, spoken only in a natural gap. */
    ROUTINE
}

data class GuidanceUtterance(
    val text: String,
    val urgency: UrgencyLevel,
    /** Haptic pattern name to fire alongside speech, or null if speech-only. */
    val hapticPattern: String? = null
)

/** A single hazard observation bundled with its computed scoring, for guidance consumption. */
data class HazardObservation(
    val hazardClass: String,
    val distanceMeters: Float?,
    val direction: Direction,
    val scoring: ScoringResult,
    val predictedTimeToCollisionSeconds: Float? = null
)

object GuidanceEngine {

    // ---- Hazard -> spoken warning -----------------------------------------

    /**
     * Builds a spoken hazard warning from a fiducial distance estimate,
     * bearing, and hazard score. This is the core "what should Auriga say
     * right now" function for a single detected hazard.
     */
    fun describeHazard(
        hazardClass: String,
        humanLabel: String,
        distance: FiducialEstimationResult?,
        direction: Direction,
        scoring: ScoringResult,
        ttcSeconds: Float? = null
    ): GuidanceUtterance {
        val directionPhrase = BearingEngine.describeDirection(direction)
        val distancePhrase = distance?.let { formatDistance(it.estimatedDistanceM) } ?: "nearby"

        val urgency = when {
            scoring.riskScore >= 0.75f -> UrgencyLevel.CRITICAL
            scoring.riskScore >= 0.45f -> UrgencyLevel.ELEVATED
            else -> UrgencyLevel.ROUTINE
        }

        val text = if (ttcSeconds != null && ttcSeconds < 3f) {
            // Moving hazard on a near-term collision course — most urgent phrasing.
            "$humanLabel $directionPhrase, closing fast, $distancePhrase. Move now."
        } else {
            "$humanLabel $directionPhrase, $distancePhrase."
        }

        val haptic = when (urgency) {
            UrgencyLevel.CRITICAL -> "pulse_rapid_${direction.name.lowercase()}"
            UrgencyLevel.ELEVATED -> "pulse_single_${direction.name.lowercase()}"
            UrgencyLevel.ROUTINE -> null
        }

        return GuidanceUtterance(text = text, urgency = urgency, hapticPattern = haptic)
    }

    // ---- Active navigation: "turn until beep" -----------------------------

    /**
     * During active checkpoint navigation, tells the user which way to turn
     * to face the checkpoint, expressed as a beep-guided correction rather
     * than a numeric heading (numeric headings are not useful without sight).
     *
     * toleranceDegrees: once |headingErrorDegrees| is within this band, the
     * user is considered correctly oriented and guidance stops issuing turn
     * instructions (silent — no need to narrate "you are facing the right way"
     * repeatedly).
     */
    fun turnUntilBeepInstruction(
        headingErrorDegrees: Float,
        toleranceDegrees: Float = 8f
    ): GuidanceUtterance? {
        val absError = kotlin.math.abs(headingErrorDegrees)
        if (absError <= toleranceDegrees) {
            // Within tolerance — no instruction needed, this is the "beep" moment.
            return GuidanceUtterance(
                text = "",
                urgency = UrgencyLevel.ROUTINE,
                hapticPattern = "beep_aligned"
            )
        }

        val turnDirection = if (headingErrorDegrees < 0) "left" else "right"
        // Larger error -> more urgent-sounding correction, but never CRITICAL
        // (turning is routine guidance, not danger).
        val magnitudeWord = when {
            absError > 60f -> "sharply"
            absError > 25f -> ""
            else -> "slightly"
        }

        val text = listOf("Turn", magnitudeWord, turnDirection)
            .filter { it.isNotEmpty() }
            .joinToString(" ") + "."

        return GuidanceUtterance(
            text = text,
            urgency = UrgencyLevel.ELEVATED,
            hapticPattern = "pulse_single_$turnDirection"
        )
    }

    // ---- Checkpoint-relative distance updates -----------------------------

    /**
     * Periodic distance updates during navigation toward a checkpoint, fired
     * roughly every 25% of remaining distance traveled (not on a fixed timer),
     * so the frequency naturally adapts to walking pace.
     *
     * Call this whenever remainingMeters changes; it internally tracks the
     * last-announced fraction via [lastAnnouncedFraction] which the caller owns
     * and persists across calls (kept external/stateless here to avoid hidden
     * mutable engine state).
     */
    fun checkpointUpdateIfDue(
        remainingMeters: Float,
        totalMeters: Float,
        lastAnnouncedFraction: Float
    ): Pair<GuidanceUtterance?, Float> {
        if (totalMeters <= 0f) return null to lastAnnouncedFraction

        val remainingFraction = (remainingMeters / totalMeters).coerceIn(0f, 1f)
        val traveledFraction = 1f - remainingFraction

        // Announce at each 25% milestone of distance traveled.
        val milestone = (traveledFraction * 4).toInt() / 4f

        if (milestone > lastAnnouncedFraction) {
            val text = when {
                remainingMeters < 1.5f -> "Checkpoint right ahead."
                else -> "${formatDistance(remainingMeters)} to go."
            }
            return GuidanceUtterance(text, UrgencyLevel.ROUTINE) to milestone
        }

        return null to lastAnnouncedFraction
    }

    // ---- Helpers -----------------------------------------------------------

    private fun formatDistance(meters: Float): String = when {
        meters < 1f -> "less than a meter away"
        meters < 10f -> "${meters.toInt()} meter${if (meters.toInt() == 1) "" else "s"} away"
        else -> "${(meters / 5).toInt() * 5} meters away" // round to nearest 5m beyond 10m
    }
}
