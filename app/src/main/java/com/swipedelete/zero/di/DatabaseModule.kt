package com.swipedelete.zero.di

import android.content.Context
import androidx.room.Room
import com.swipedelete.zero.data.local.AppDatabase
import com.swipedelete.zero.data.local.BackupReceiptDao
import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.DeckSessionDao
import com.swipedelete.zero.data.local.ExclusionDao
import com.swipedelete.zero.data.local.KeptFileDao
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.local.StagedFileDao
import com.swipedelete.zero.data.repository.BackupRepository
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

    val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `backup_receipts` (
                `contentUri` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `remoteId` TEXT NOT NULL,
                `originalSha256` TEXT NOT NULL,
                `originalSizeBytes` INTEGER NOT NULL,
                `displayName` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `verifiedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`contentUri`, `provider`, `accountId`)
            )""".trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_receipts_provider_accountId_remoteId` ON `backup_receipts` (`provider`, `accountId`, `remoteId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides fun provideStagedFileDao(db: AppDatabase): StagedFileDao = db.stagedFileDao()
    @Provides fun provideDeckSessionDao(db: AppDatabase): DeckSessionDao = db.deckSessionDao()
    @Provides fun provideExclusionDao(db: AppDatabase): ExclusionDao = db.exclusionDao()
    @Provides fun provideMediaAnalysisDao(db: AppDatabase): MediaAnalysisDao = db.mediaAnalysisDao()
    @Provides fun provideKeptFileDao(db: AppDatabase): KeptFileDao = db.keptFileDao()
    @Provides fun provideBackedUpFileDao(db: AppDatabase): BackedUpFileDao = db.backedUpFileDao()
    @Provides fun provideBackupReceiptDao(db: AppDatabase): BackupReceiptDao = db.backupReceiptDao()
    @Provides @Singleton fun provideBackupRepository(
        kept: com.swipedelete.zero.data.local.KeptFileDao,
        backed: BackedUpFileDao,
        uploads: CloudUploadDao,
        receipts: BackupReceiptDao,
    ): BackupRepository = BackupRepository(kept, backed, uploads, receipts)
    @Provides fun provideCloudUploadDao(db: AppDatabase): CloudUploadDao = db.cloudUploadDao()
}
