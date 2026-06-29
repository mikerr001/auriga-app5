package com.drakosanctis.auriga.vision

import kotlin.math.hypot

/**
 * Auriga Object Tracker
 *
 * A deliberately simple frame-to-frame tracker — centroid-distance matching,
 * not a full multi-object tracking system (no Kalman filter, no DeepSORT-style
 * appearance embedding). This is sufficient for MVP v1's purpose: giving
 * HazardPredictionEngine a real approach/recede velocity instead of always
 * receiving null.
 *
 * Matching strategy:
 * - For each new detection, find the closest existing track of the SAME
 *   hazard-relevant class within [maxMatchDistancePx] pixels of its last
 *   known position.
 * - If matched, update that track's position/distance/timestamp history
 *   and compute velocity from the change in estimated real-world distance
 *   over elapsed time.
 * - If unmatched, start a new track.
 * - Tracks not matched for [maxMissedFrames] consecutive frames are dropped
 *   (handles the "object briefly occluded" case without immediately losing
 *   the track, while still cleaning up tracks for objects that actually
 *   left the frame).
 */

data class TrackedObject(
    val trackId: Long,
    val label: String,
    val lastCenterX: Float,
    val lastCenterY: Float,
    val lastDistanceMeters: Float?,
    val lastTimestampMs: Long,
    val velocityMs: Float?,
    val missedFrameCount: Int
)

class ObjectTracker(
    private val maxMatchDistancePx: Float = 120f,
    private val maxMissedFrames: Int = 5,
    /** Velocity readings are smoothed across this many recent samples to reduce noise. */
    private val velocitySmoothingWindow: Int = 3
) {
    private var nextTrackId = 1L
    private val activeTracks = mutableMapOf<Long, MutableTrack>()

    private class MutableTrack(
        var trackId: Long,
        var label: String,
        var centerX: Float,
        var centerY: Float,
        var distanceMeters: Float?,
        var timestampMs: Long,
        var missedFrameCount: Int,
        val recentVelocities: MutableList<Float> = mutableListOf()
    )

    /**
     * Updates tracking state with this frame's detections and returns, for
     * each input detection, the matched track's smoothed velocity (negative
     * = approaching, positive = receding; null if no reliable velocity yet —
     * e.g. brand-new track with no prior sample).
     *
     * Call once per analyzed frame with ALL of that frame's detections
     * (paired with their estimated distance), in detection order.
     */
    fun update(
        detections: List<Pair<DetectedObject, Float?>>, // (detection, estimatedDistanceMeters)
        nowMs: Long = System.currentTimeMillis()
    ): Map<DetectedObject, Float?> {
        val results = mutableMapOf<DetectedObject, Float?>()
        val matchedTrackIds = mutableSetOf<Long>()

        for ((detection, distanceMeters) in detections) {
            val candidate = findClosestTrack(detection, matchedTrackIds)

            if (candidate != null) {
                val velocity = updateTrack(candidate, detection, distanceMeters, nowMs)
                matchedTrackIds.add(candidate.trackId)
                results[detection] = velocity
            } else {
                val newTrack = createTrack(detection, distanceMeters, nowMs)
                matchedTrackIds.add(newTrack.trackId)
                results[detection] = null // no velocity yet on a brand-new track
            }
        }

        pruneStaleTracks(matchedTrackIds)
        return results
    }

    private fun findClosestTrack(
        detection: DetectedObject,
        alreadyMatched: Set<Long>
    ): MutableTrack? {
        var best: MutableTrack? = null
        var bestDistance = maxMatchDistancePx

        for (track in activeTracks.values) {
            if (track.trackId in alreadyMatched) continue
            if (track.label != detection.label) continue

            val dx = track.centerX - detection.centerX
            val dy = track.centerY - detection.centerY
            val pixelDistance = hypot(dx, dy)

            if (pixelDistance <= bestDistance) {
                bestDistance = pixelDistance
                best = track
            }
        }
        return best
    }

    private fun createTrack(
        detection: DetectedObject,
        distanceMeters: Float?,
        nowMs: Long
    ): MutableTrack {
        val track = MutableTrack(
            trackId = nextTrackId++,
            label = detection.label,
            centerX = detection.centerX,
            centerY = detection.centerY,
            distanceMeters = distanceMeters,
            timestampMs = nowMs,
            missedFrameCount = 0
        )
        activeTracks[track.trackId] = track
        return track
    }

    /** Returns the smoothed velocity in m/s (negative = approaching) after updating the track. */
    private fun updateTrack(
        track: MutableTrack,
        detection: DetectedObject,
        distanceMeters: Float?,
        nowMs: Long
    ): Float? {
        val elapsedSeconds = (nowMs - track.timestampMs) / 1000f

        var instantVelocity: Float? = null
        if (distanceMeters != null && track.distanceMeters != null && elapsedSeconds > 0.01f) {
            // Negative = distance shrinking = approaching.
            instantVelocity = (distanceMeters - track.distanceMeters!!) / elapsedSeconds
            track.recentVelocities.add(instantVelocity)
            if (track.recentVelocities.size > velocitySmoothingWindow) {
                track.recentVelocities.removeAt(0)
            }
        }

        track.centerX = detection.centerX
        track.centerY = detection.centerY
        track.distanceMeters = distanceMeters
        track.timestampMs = nowMs
        track.missedFrameCount = 0

        if (track.recentVelocities.isEmpty()) return null
        return track.recentVelocities.average().toFloat()
    }

    private fun pruneStaleTracks(matchedTrackIds: Set<Long>) {
        val toRemove = mutableListOf<Long>()
        for ((id, track) in activeTracks) {
            if (id !in matchedTrackIds) {
                track.missedFrameCount++
                if (track.missedFrameCount > maxMissedFrames) {
                    toRemove.add(id)
                }
            }
        }
        toRemove.forEach { activeTracks.remove(it) }
    }

    fun reset() {
        activeTracks.clear()
    }
}
