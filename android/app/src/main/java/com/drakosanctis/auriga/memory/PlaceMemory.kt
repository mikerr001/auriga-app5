package com.drakosanctis.auriga.memory

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Auriga Place Memory Lite
 *
 * Designed from the Auriga product deliberations (auriga-place-memory repo
 * contained only a placeholder README — no schema or logic to port).
 *
 * Scope, as agreed in the MVP-v1 scoping discussion:
 * - Lightweight per-place storage: "I've been here before."
 * - Stores known object labels per place (e.g. living_room -> [sofa, door, tv]).
 * - Explicitly NOT full SLAM / 3D reconstruction / persistent spatial graph —
 *   that's Phase 5+ ("Indoor World Builder") in the long-term roadmap, out of
 *   scope for MVP v1.
 */

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "label") val label: String, // user-assigned name, e.g. "living_room"
    @ColumnInfo(name = "created_at") val createdAtMs: Long,
    @ColumnInfo(name = "last_visited_at") val lastVisitedAtMs: Long,
    @ColumnInfo(name = "visit_count") val visitCount: Int = 1
)

@Entity(tableName = "place_objects")
data class PlaceObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "place_id") val placeId: Long,
    @ColumnInfo(name = "object_class") val objectClass: String, // e.g. "sofa", "door", "tv"
    @ColumnInfo(name = "observation_count") val observationCount: Int = 1,
    @ColumnInfo(name = "last_seen_at") val lastSeenAtMs: Long
)

@Dao
interface PlaceMemoryDao {

    @Query("SELECT * FROM places ORDER BY last_visited_at DESC")
    suspend fun getAllPlaces(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE label = :label LIMIT 1")
    suspend fun findByLabel(label: String): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: PlaceEntity): Long

    @Update
    suspend fun updatePlace(place: PlaceEntity)

    @Query("SELECT * FROM place_objects WHERE place_id = :placeId")
    suspend fun getObjectsForPlace(placeId: Long): List<PlaceObjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaceObject(obj: PlaceObjectEntity): Long

    @Query("""
        SELECT * FROM place_objects
        WHERE place_id = :placeId AND object_class = :objectClass
        LIMIT 1
    """)
    suspend fun findObjectInPlace(placeId: Long, objectClass: String): PlaceObjectEntity?

    @Update
    suspend fun updatePlaceObject(obj: PlaceObjectEntity)
}

/**
 * Higher-level API used by the rest of the app. Wraps the DAO with the
 * "have I been here before" logic and the confidence boost that feeds into
 * HazardScoringEngine's placeMemoryBoost parameter.
 */
class PlaceMemoryRepository(private val dao: PlaceMemoryDao) {

    /** Records a visit to a place, creating it if new, and bumping visit count if known. */
    suspend fun recordVisit(label: String, nowMs: Long = System.currentTimeMillis()): PlaceEntity {
        val existing = dao.findByLabel(label)
        return if (existing != null) {
            val updated = existing.copy(
                lastVisitedAtMs = nowMs,
                visitCount = existing.visitCount + 1
            )
            dao.updatePlace(updated)
            updated
        } else {
            val newPlace = PlaceEntity(
                label = label,
                createdAtMs = nowMs,
                lastVisitedAtMs = nowMs,
                visitCount = 1
            )
            val id = dao.insertPlace(newPlace)
            newPlace.copy(id = id)
        }
    }

    /** Records (or reinforces) an observed object within a known place. */
    suspend fun recordObjectObservation(
        placeId: Long,
        objectClass: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val existing = dao.findObjectInPlace(placeId, objectClass)
        if (existing != null) {
            dao.updatePlaceObject(
                existing.copy(
                    observationCount = existing.observationCount + 1,
                    lastSeenAtMs = nowMs
                )
            )
        } else {
            dao.insertPlaceObject(
                PlaceObjectEntity(
                    placeId = placeId,
                    objectClass = objectClass,
                    lastSeenAtMs = nowMs
                )
            )
        }
    }

    /**
     * Confidence boost for HazardScoringEngine: if an object class has been
     * reliably observed in this place before, slightly raise detector confidence
     * (capped at 0.15 per the scoring engine's expected range).
     */
    suspend fun confidenceBoostFor(placeId: Long, objectClass: String): Float {
        val obj = dao.findObjectInPlace(placeId, objectClass) ?: return 0f
        return when {
            obj.observationCount >= 5 -> 0.15f
            obj.observationCount >= 2 -> 0.08f
            else -> 0f
        }
    }

    suspend fun isKnownPlace(label: String): Boolean = dao.findByLabel(label) != null
}
