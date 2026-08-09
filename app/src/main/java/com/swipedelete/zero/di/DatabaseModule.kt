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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // v1 schema; destructive fallback is fine — the DB only holds
            // regenerable queue/session/analysis state, never user files.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideStagedFileDao(db: AppDatabase): StagedFileDao = db.stagedFileDao()
    @Provides fun provideDeckSessionDao(db: AppDatabase): DeckSessionDao = db.deckSessionDao()
    @Provides fun provideExclusionDao(db: AppDatabase): ExclusionDao = db.exclusionDao()
    @Provides fun provideMediaAnalysisDao(db: AppDatabase): MediaAnalysisDao = db.mediaAnalysisDao()
    @Provides fun provideKeptFileDao(db: AppDatabase): KeptFileDao = db.keptFileDao()
    @Provides fun provideBackedUpFileDao(db: AppDatabase): BackedUpFileDao = db.backedUpFileDao()
    @Provides fun provideCloudUploadDao(db: AppDatabase): CloudUploadDao = db.cloudUploadDao()
}
