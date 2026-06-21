package com.drakosanctis.auriga.memory

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Auriga Continuous Learning Lite
 *
 * Designed from the Auriga product deliberations (auriga-continuous-learning
 * repo was an empty scaffold — no implementation existed to port).
 *
 * CRITICAL SCOPE BOUNDARY, explicitly agreed in the MVP-v1 scoping discussion:
 * - This is NOT online model retraining, NOT weight updates, NOT adaptive
 *   behavior change at runtime. That was explicitly rejected as "too risky."
 * - This IS structured logging only: predicted class vs. user correction,
 *   timestamped, stored locally for later human review (e.g. exported and
 *   examined by the developer to improve future model versions offline).
 * - No automatic behavior change ever results from this data on-device.
 */

@Entity(tableName = "correction_logs")
data class CorrectionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "predicted_class") val predictedClass: String,
    @ColumnInfo(name = "corrected_class") val correctedClass: String,
    @ColumnInfo(name = "confidence_at_prediction") val confidenceAtPrediction: Float,
    @ColumnInfo(name = "distance_at_prediction_m") val distanceAtPredictionM: Float?,
    @ColumnInfo(name = "timestamp") val timestampMs: Long,
    @ColumnInfo(name = "exported") val exported: Boolean = false
)

@Entity(tableName = "failure_logs")
data class FailureLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "component") val component: String, // e.g. "fiducial_engine", "model_load"
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "timestamp") val timestampMs: Long,
    @ColumnInfo(name = "exported") val exported: Boolean = false
)

@Dao
interface ContinuousLearningDao {

    @Insert
    suspend fun logCorrection(entry: CorrectionLogEntity): Long

    @Insert
    suspend fun logFailure(entry: FailureLogEntity): Long

    @Query("SELECT * FROM correction_logs WHERE exported = 0 ORDER BY timestamp ASC")
    suspend fun getUnexportedCorrections(): List<CorrectionLogEntity>

    @Query("SELECT * FROM failure_logs WHERE exported = 0 ORDER BY timestamp ASC")
    suspend fun getUnexportedFailures(): List<FailureLogEntity>

    @Query("UPDATE correction_logs SET exported = 1 WHERE id IN (:ids)")
    suspend fun markCorrectionsExported(ids: List<Long>)

    @Query("UPDATE failure_logs SET exported = 1 WHERE id IN (:ids)")
    suspend fun markFailuresExported(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM correction_logs WHERE predicted_class = :predicted AND corrected_class = :corrected")
    suspend fun countSpecificCorrection(predicted: String, corrected: String): Int
}

/**
 * Simple repository wrapper. Pure data collection — never feeds back into
 * any model weights or runtime detection thresholds automatically.
 */
class ContinuousLearningRepository(private val dao: ContinuousLearningDao) {

    suspend fun recordCorrection(
        predictedClass: String,
        correctedClass: String,
        confidenceAtPrediction: Float,
        distanceAtPredictionM: Float?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (predictedClass == correctedClass) return // not actually a correction
        dao.logCorrection(
            CorrectionLogEntity(
                predictedClass = predictedClass,
                correctedClass = correctedClass,
                confidenceAtPrediction = confidenceAtPrediction,
                distanceAtPredictionM = distanceAtPredictionM,
                timestampMs = nowMs
            )
        )
    }

    suspend fun recordFailure(component: String, description: String, nowMs: Long = System.currentTimeMillis()) {
        dao.logFailure(FailureLogEntity(component = component, description = description, timestampMs = nowMs))
    }

    /** Returns and marks-exported all pending logs, for the developer to pull off-device for review. */
    suspend fun exportPendingLogs(): Pair<List<CorrectionLogEntity>, List<FailureLogEntity>> {
        val corrections = dao.getUnexportedCorrections()
        val failures = dao.getUnexportedFailures()
        if (corrections.isNotEmpty()) dao.markCorrectionsExported(corrections.map { it.id })
        if (failures.isNotEmpty()) dao.markFailuresExported(failures.map { it.id })
        return corrections to failures
    }
}
