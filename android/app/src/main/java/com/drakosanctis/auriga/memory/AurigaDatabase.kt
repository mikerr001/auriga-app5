package com.drakosanctis.auriga.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaceEntity::class,
        PlaceObjectEntity::class,
        CorrectionLogEntity::class,
        FailureLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AurigaDatabase : RoomDatabase() {
    abstract fun placeMemoryDao(): PlaceMemoryDao
    abstract fun continuousLearningDao(): ContinuousLearningDao

    companion object {
        @Volatile
        private var instance: AurigaDatabase? = null

        fun getInstance(context: Context): AurigaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AurigaDatabase::class.java,
                    "auriga.db"
                ).build().also { instance = it }
            }
        }
    }
}
