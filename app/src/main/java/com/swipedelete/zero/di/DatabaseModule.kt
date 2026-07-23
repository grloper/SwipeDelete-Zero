package com.swipedelete.zero.di

import android.content.Context
import androidx.room.Room
import com.swipedelete.zero.data.local.AppDatabase
import com.swipedelete.zero.data.local.DeckSessionDao
import com.swipedelete.zero.data.local.ExclusionDao
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
}
