package com.swipedelete.zero.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StagedFileEntity::class,
        DeckSessionEntity::class,
        ExclusionEntity::class,
        MediaAnalysisEntity::class,
        KeptFileEntity::class,
        BackedUpFileEntity::class,
        CloudUploadEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stagedFileDao(): StagedFileDao
    abstract fun deckSessionDao(): DeckSessionDao
    abstract fun exclusionDao(): ExclusionDao
    abstract fun mediaAnalysisDao(): MediaAnalysisDao
    abstract fun keptFileDao(): KeptFileDao
    abstract fun backedUpFileDao(): BackedUpFileDao
    abstract fun cloudUploadDao(): CloudUploadDao

    companion object {
        const val NAME = "swipedelete-zero.db"
    }
}
