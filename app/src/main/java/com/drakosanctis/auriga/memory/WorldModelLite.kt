package com.drakosanctis.auriga.memory

/**
 * Auriga World Model Lite
 *
 * Designed from the Auriga product deliberations (auriga-world-model repo
 * was an empty scaffold — no implementation existed to port).
 *
 * Scope, as agreed in the MVP-v1 scoping discussion:
 * - "World Model Lite", explicitly NOT "World Model Ultra":
 *   current room + known objects + known exits + recent observations.
 * - No 3D scene reconstruction, no semantic maps, no persistent environmental
 *   graph. This is short-term working memory, reset or decayed over time —
 *   not a permanent spatial database (that's Place Memory's job, and even
 *   Place Memory stays lightweight per MVP scope).
 */

data class RecentHazard(
    val hazardClass: String,
    val lastSeenAtMs: Long,
    val approximateDistanceM: Float?
)

data class WorldModelSnapshot(
    val currentPlaceLabel: String?,
    val knownExits: List<String>,
    val recentHazards: List<RecentHazard>
)

/**
 * In-memory, non-persistent working model of "what do I currently understand
 * about where I am right now." Deliberately ephemeral — survives the current
 * session/activity lifecycle but is not written to disk. Long-term place
 * knowledge belongs to PlaceMemoryRepository instead.
 */
class WorldModelLite(
    private val hazardRetentionMs: Long = 8_000L
) {
    private var currentPlaceLabel: String? = null
    private val knownExits = mutableSetOf<String>()
    private val recentHazards = mutableListOf<RecentHazard>()

    fun setCurrentPlace(label: String?) {
        if (label != currentPlaceLabel) {
            // Entering a new place resets exit knowledge for the new context;
            // hazards are kept briefly since recent danger doesn't vanish at
            // a doorway threshold.
            knownExits.clear()
        }
        currentPlaceLabel = label
    }

    fun addKnownExit(exitLabel: String) {
        knownExits.add(exitLabel)
    }

    fun recordHazardObservation(
        hazardClass: String,
        distanceMeters: Float?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        recentHazards.removeAll { it.hazardClass == hazardClass }
        recentHazards.add(RecentHazard(hazardClass, nowMs, distanceMeters))
        pruneStaleHazards(nowMs)
    }

    private fun pruneStaleHazards(nowMs: Long) {
        recentHazards.removeAll { (nowMs - it.lastSeenAtMs) > hazardRetentionMs }
    }

    fun snapshot(nowMs: Long = System.currentTimeMillis()): WorldModelSnapshot {
        pruneStaleHazards(nowMs)
        return WorldModelSnapshot(
            currentPlaceLabel = currentPlaceLabel,
            knownExits = knownExits.toList(),
            recentHazards = recentHazards.toList()
        )
    }

    fun reset() {
        currentPlaceLabel = null
        knownExits.clear()
        recentHazards.clear()
    }
}
