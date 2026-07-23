package com.swipedelete.zero.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StagedFileEntity::class,
        DeckSessionEntity::class,
        ExclusionEntity::class,
        MediaAnalysisEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stagedFileDao(): StagedFileDao
    abstract fun deckSessionDao(): DeckSessionDao
    abstract fun exclusionDao(): ExclusionDao
    abstract fun mediaAnalysisDao(): MediaAnalysisDao

    companion object {
        const val NAME = "swipedelete-zero.db"
    }
}
