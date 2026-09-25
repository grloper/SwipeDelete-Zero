package com.swipedelete.zero.di

import android.content.Context
import androidx.room.Room
import com.swipedelete.zero.data.local.AppDatabase
import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.DeckSessionDao
import com.swipedelete.zero.data.local.ExclusionDao
import com.swipedelete.zero.data.local.KeptFileDao
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.local.StagedFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cloud_uploads ADD COLUMN accountName TEXT DEFAULT NULL")
            db.execSQL("UPDATE cloud_uploads SET state = 'FAILED', lastError = 'Quarantined: legacy upload without account ownership' WHERE accountName IS NULL AND state IN ('QUEUED', 'UPLOADING', 'VERIFYING')")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_4_5)
            .build()

    @Provides fun provideStagedFileDao(db: AppDatabase): StagedFileDao = db.stagedFileDao()
    @Provides fun provideDeckSessionDao(db: AppDatabase): DeckSessionDao = db.deckSessionDao()
    @Provides fun provideExclusionDao(db: AppDatabase): ExclusionDao = db.exclusionDao()
    @Provides fun provideMediaAnalysisDao(db: AppDatabase): MediaAnalysisDao = db.mediaAnalysisDao()
    @Provides fun provideKeptFileDao(db: AppDatabase): KeptFileDao = db.keptFileDao()
    @Provides fun provideBackedUpFileDao(db: AppDatabase): BackedUpFileDao = db.backedUpFileDao()
    @Provides fun provideCloudUploadDao(db: AppDatabase): CloudUploadDao = db.cloudUploadDao()
}
