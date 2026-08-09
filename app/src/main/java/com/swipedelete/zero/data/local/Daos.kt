package com.swipedelete.zero.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StagedFileDao {

    @Query("SELECT * FROM staged_files ORDER BY stagedAtMillis DESC")
    fun observeAll(): Flow<List<StagedFileEntity>>

    @Query("SELECT COUNT(*) FROM staged_files")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM staged_files")
    fun observeStagedBytes(): Flow<Long>

    @Query("SELECT * FROM staged_files")
    suspend fun getAll(): List<StagedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stage(file: StagedFileEntity)

    @Query("DELETE FROM staged_files WHERE contentUri = :uri")
    suspend fun unstage(uri: String)

    /** Remove a batch — used after a partially-successful purge (only winners). */
    @Query("DELETE FROM staged_files WHERE contentUri IN (:uris)")
    suspend fun removeAll(uris: List<String>)

    @Query("DELETE FROM staged_files")
    suspend fun clear()
}

@Dao
interface KeptFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: KeptFileEntity)

    @Query("DELETE FROM kept_files WHERE contentUri = :uri")
    suspend fun remove(uri: String)

    /** Kept files with no row in the backup ledger — the incremental work-list. */
    @Query(
        "SELECT * FROM kept_files WHERE contentUri NOT IN " +
            "(SELECT contentUri FROM backed_up_files) ORDER BY keptAtMillis"
    )
    suspend fun pendingBackup(): List<KeptFileEntity>

    @Query(
        "SELECT COUNT(*) FROM kept_files WHERE contentUri NOT IN " +
            "(SELECT contentUri FROM backed_up_files)"
    )
    fun observePendingBackupCount(): Flow<Int>
}

@Dao
interface BackedUpFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: BackedUpFileEntity)

    @Query("SELECT COUNT(*) FROM backed_up_files")
    fun observeCount(): Flow<Int>

    /** Every backed-up uri — powers the per-card Cloud Verification chip. */
    @Query("SELECT contentUri FROM backed_up_files")
    fun observeBackedUpUris(): Flow<List<String>>
}

@Dao
interface DeckSessionDao {

    @Query("SELECT * FROM deck_sessions WHERE deckId = :deckId")
    suspend fun get(deckId: String): DeckSessionEntity?

    @Query("SELECT * FROM deck_sessions")
    fun observeAll(): Flow<List<DeckSessionEntity>>

    @Upsert
    suspend fun upsert(session: DeckSessionEntity)

    @Query("DELETE FROM deck_sessions WHERE deckId = :deckId")
    suspend fun delete(deckId: String)
}

@Dao
interface ExclusionDao {

    @Query("SELECT * FROM exclusions ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<ExclusionEntity>>

    @Query("SELECT perceptualHash FROM exclusions WHERE perceptualHash IS NOT NULL")
    suspend fun excludedHashes(): List<Long>

    @Query("SELECT folderPath FROM exclusions WHERE folderPath IS NOT NULL")
    suspend fun excludedFolders(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(exclusion: ExclusionEntity)

    @Query("DELETE FROM exclusions WHERE id = :id")
    suspend fun remove(id: Long)
}

@Dao
interface CloudUploadDao {

    @Query("SELECT * FROM cloud_uploads ORDER BY enqueuedAtMillis")
    fun observeAll(): Flow<List<CloudUploadEntity>>

    @Query("SELECT * FROM cloud_uploads WHERE contentUri = :uri")
    suspend fun get(uri: String): CloudUploadEntity?

    /** Oldest row that still needs work (not yet verified, not terminally failed). */
    @Query(
        "SELECT * FROM cloud_uploads WHERE state IN " +
            "('QUEUED', 'UPLOADING', 'VERIFYING') ORDER BY enqueuedAtMillis LIMIT 1"
    )
    suspend fun nextPending(): CloudUploadEntity?

    @Upsert
    suspend fun upsert(entity: CloudUploadEntity)

    /** Cancel an up-swipe that hasn't started uploading yet (Undo path). */
    @Query("DELETE FROM cloud_uploads WHERE contentUri = :uri AND state = 'QUEUED'")
    suspend fun deleteIfQueued(uri: String): Int

    @Query("DELETE FROM cloud_uploads WHERE contentUri = :uri")
    suspend fun delete(uri: String)
}

@Dao
interface MediaAnalysisDao {

    @Query("SELECT * FROM media_analysis WHERE mediaId = :id")
    suspend fun get(id: Long): MediaAnalysisEntity?

    @Query("SELECT mediaId FROM media_analysis")
    suspend fun analyzedIds(): List<Long>

    @Query("SELECT * FROM media_analysis WHERE isBlurry = 1")
    suspend fun blurryItems(): List<MediaAnalysisEntity>

    @Query("SELECT * FROM media_analysis ORDER BY pHash")
    suspend fun allByHash(): List<MediaAnalysisEntity>

    @Upsert
    suspend fun upsert(entity: MediaAnalysisEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MediaAnalysisEntity>)

    @Query("DELETE FROM media_analysis WHERE mediaId IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)
}
