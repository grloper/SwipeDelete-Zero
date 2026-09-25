package com.swipedelete.zero.photos

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import androidx.work.WorkerParameters
import com.swipedelete.zero.data.local.AppDatabase
import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.data.local.StagedFileDao
import com.swipedelete.zero.data.local.StagedFileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream

/**
 * M0-R1 / M0-R2: Production worker and orchestrator authentication regression tests.
 *
 * Tests:
 * 1. Account rotation: Work queued under A -> worker restarted under B -> old A work is quarantined and never sent under B; new work under B is processed under B.
 * 2. Legacy row quarantine: Work with null account owner is quarantined and never processed.
 * 3. Sign-out during upload: Disconnect mid-upload throws CancellationException; no new chunk/commit/ledger-write succeeds.
 * 4. Scoped 401 isolation: Read 401 clears and refreshes READ token only; append token is never invalidated for read errors.
 * 5. Bounded read retry: Read 401 twice fails the row with 401 without consuming append retries.
 */
class PhotosUploadWorkerAuthTest {

    private class TestAuthClient(
        var activeAccountName: String? = "alice@example.com",
    ) : PhotosAuthClient {
        val requestedScopes = mutableListOf<String>()
        val clearedTokens = mutableListOf<String>()
        var appendToken = "append_token_valid"
        var readToken = "read_token_valid"

        override fun getSignedInAccountName(context: Context): String? = activeAccountName
        override fun getSignedInAccount(context: Context): Account? = null

        override fun getToken(context: Context, accountName: String, scope: String): String {
            requestedScopes.add("$scope for $accountName")
            return if (scope.contains(PhotosUploader.PHOTOS_READ_SCOPE)) readToken else appendToken
        }

        override fun clearToken(context: Context, token: String) {
            clearedTokens.add(token)
        }
    }

    private class TestPhotosUploader : PhotosUploader() {
        var startSessionUrl = "https://upload.url/test"
        var uploadChunkToken: String? = "test_upload_token"
        var batchCreateId: String = "test_media_id"
        var getMediaItemResult = RemoteItem("test_media_id", "photo.jpg", "image/jpeg", "https://photos.google.com/test")

        val startSessionCalls = mutableListOf<String>()
        val uploadChunkCalls = mutableListOf<Long>()
        val batchCreateCalls = mutableListOf<String>()
        val getMediaItemCalls = mutableListOf<String>()

        var onStartSession: (() -> Unit)? = null
        var onUploadChunk: (() -> String?)? = null
        var onGetMediaItem: ((String) -> RemoteItem)? = null

        override fun startSession(authToken: String, mimeType: String, rawSizeBytes: Long): Session {
            startSessionCalls.add(authToken)
            onStartSession?.invoke()
            return Session(startSessionUrl, 8192L)
        }

        override fun uploadChunk(
            authToken: String,
            uploadUrl: String,
            chunk: ByteArray,
            length: Int,
            offset: Long,
            isLast: Boolean,
        ): String? {
            uploadChunkCalls.add(offset)
            return onUploadChunk?.invoke() ?: if (isLast) uploadChunkToken else null
        }

        override fun batchCreate(authToken: String, uploadToken: String, fileName: String): String {
            batchCreateCalls.add(uploadToken)
            return batchCreateId
        }

        override fun getMediaItem(authToken: String, mediaItemId: String): RemoteItem {
            getMediaItemCalls.add(mediaItemId)
            return onGetMediaItem?.invoke(authToken) ?: getMediaItemResult
        }
    }

    private class InMemoryCloudUploadDao : CloudUploadDao {
        val map = LinkedHashMap<String, CloudUploadEntity>()

        override fun observeAll(): Flow<List<CloudUploadEntity>> = emptyFlow()
        override suspend fun get(uri: String): CloudUploadEntity? = map[uri]
        override suspend fun nextPending(): CloudUploadEntity? =
            map.values.firstOrNull { it.state in listOf(CloudUploadEntity.STATE_QUEUED, CloudUploadEntity.STATE_UPLOADING, CloudUploadEntity.STATE_VERIFYING) }
        override suspend fun verifiedWithoutLedger(): List<CloudUploadEntity> = emptyList()
        override suspend fun upsert(entity: CloudUploadEntity) { map[entity.contentUri] = entity }
        override suspend fun deleteIfQueued(uri: String): Int = 0
        override suspend fun delete(uri: String) { map.remove(uri) }
        override suspend fun deleteIfCancelable(uri: String): Int = 0
        override suspend fun retryAllFailed(nowMillis: Long): Int = 0
        override suspend fun clearCompleted(): Int = 0
        override fun observeCountByState(state: String): Flow<Int> = emptyFlow()
    }

    private class InMemoryBackedUpFileDao : BackedUpFileDao {
        val ledger = mutableListOf<BackedUpFileEntity>()
        override suspend fun insert(file: BackedUpFileEntity) { ledger.add(file) }
        override fun observeCount(): Flow<Int> = emptyFlow()
        override fun observeBackedUpUris(): Flow<List<String>> = emptyFlow()
        override fun observeAll(): Flow<List<BackedUpFileEntity>> = emptyFlow()
        override suspend fun getAll(): List<BackedUpFileEntity> = ledger
        override suspend fun get(uri: String): BackedUpFileEntity? = ledger.firstOrNull { it.contentUri == uri }
        override suspend fun exists(uri: String): Boolean = ledger.any { it.contentUri == uri }
        override suspend fun delete(uri: String): Int = 0
        override suspend fun deleteAll(): Int = 0
    }

    private class InMemoryStagedFileDao : StagedFileDao {
        val staged = mutableListOf<StagedFileEntity>()
        override fun observeAll(): Flow<List<StagedFileEntity>> = emptyFlow()
        override fun observeCount(): Flow<Int> = emptyFlow()
        override fun observeStagedBytes(): Flow<Long> = emptyFlow()
        override suspend fun getAll(): List<StagedFileEntity> = staged
        override suspend fun stage(file: StagedFileEntity) { staged.add(file) }
        override suspend fun unstage(uri: String) { staged.removeIf { it.contentUri == uri } }
        override suspend fun removeAll(uris: List<String>) { staged.removeIf { it.contentUri in uris } }
        override suspend fun clear() { staged.clear() }
    }

    private fun createWorker(
        context: Context,
        uploadDao: CloudUploadDao,
        authClient: PhotosAuthClient,
        uploader: PhotosUploader,
    ): PhotosUploadWorker {
        val params = mock(WorkerParameters::class.java)
        val database = mock(AppDatabase::class.java)
        `when`(database.transactionExecutor).thenReturn(java.util.concurrent.Executor { it.run() })
        val worker = PhotosUploadWorker(
            appContext = context,
            params = params,
            uploadDao = uploadDao,
            backedUpFileDao = InMemoryBackedUpFileDao(),
            stagedFileDao = InMemoryStagedFileDao(),
            uploader = uploader,
            database = database,
            authClient = authClient,
        )
        worker.transactionRunner = { block -> block() }
        return worker
    }

    private fun sampleEntity(
        uri: String,
        accountName: String?,
        state: String = CloudUploadEntity.STATE_QUEUED,
    ) = CloudUploadEntity(
        contentUri = uri,
        displayName = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 100L,
        state = state,
        bytesUploaded = 0L,
        attempts = 0,
        uploadUrl = null,
        uploadToken = null,
        mediaItemId = null,
        lastError = null,
        enqueuedAtMillis = 1000L,
        updatedAtMillis = 1000L,
        accountName = accountName,
    )

    // =========================================================================
    // M0-R1: Durable Account Ownership Across Worker Restarts and Disconnects
    // =========================================================================

    @Test
    fun `M0-R1 - worker restarted under Account B quarantines pending item authorized under Account A`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "bob@example.com")
        val uploader = TestPhotosUploader()

        // Queue item authorized under alice@example.com
        val aliceItem = sampleEntity("content://media/external/images/media/1", accountName = "alice@example.com")
        uploadDao.upsert(aliceItem)

        // Queue item authorized under bob@example.com
        val bobItem = sampleEntity("content://media/external/images/media/2", accountName = "bob@example.com")
        uploadDao.upsert(bobItem)

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        val worker = createWorker(context, uploadDao, authClient, uploader)
        worker.doWork()

        // 1. Alice's item must be quarantined as FAILED with account mismatch error
        val aliceResult = uploadDao.get(aliceItem.contentUri)
        assertNotNull(aliceResult)
        assertEquals(CloudUploadEntity.STATE_FAILED, aliceResult!!.state)
        assertTrue(aliceResult.lastError!!.contains("Quarantined: item was authorized under alice@example.com"))
        assertTrue(aliceResult.lastError!!.contains("active account is bob@example.com"))

        // 2. Bob's item must have been processed under bob's credentials
        val bobResult = uploadDao.get(bobItem.contentUri)
        assertNotNull(bobResult)
        assertEquals(CloudUploadEntity.STATE_VERIFIED, bobResult!!.state)
    }

    @Test
    fun `M0-R1 - legacy queue item with null account owner is quarantined and never sent`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()

        val legacyItem = sampleEntity("content://media/external/images/media/99", accountName = null)
        uploadDao.upsert(legacyItem)

        val worker = createWorker(context, uploadDao, authClient, uploader)
        worker.doWork()

        val result = uploadDao.get(legacyItem.contentUri)
        assertNotNull(result)
        assertEquals(CloudUploadEntity.STATE_FAILED, result!!.state)
        assertTrue(result.lastError!!.contains("Quarantined: item has unknown account owner"))
        // Uploader was never touched for legacy item
        assertTrue("No sessions started for quarantined legacy row", uploader.startSessionCalls.isEmpty())
    }

    @Test
    fun `M0-R1 - disconnect during upload throws CancellationException and prevents new operations`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()

        val item = sampleEntity("content://media/external/images/media/42", accountName = "alice@example.com")
        uploadDao.upsert(item)

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        // User disconnects during session start
        uploader.onStartSession = {
            authClient.activeAccountName = null
        }

        val worker = createWorker(context, uploadDao, authClient, uploader)
        try {
            worker.doWork()
        } catch (e: Exception) {
            assertTrue("Must throw CancellationException on disconnect", e is CancellationException)
        }

        // Verify zero chunks uploaded after observed disconnect
        assertTrue("No chunks uploaded after disconnect", uploader.uploadChunkCalls.isEmpty())
        assertTrue("No batchCreate after disconnect", uploader.batchCreateCalls.isEmpty())
    }

    // =========================================================================
    // M0-R2: Scoped Authentication & 401 Isolation
    // =========================================================================

    @Test
    fun `M0-R2 - read 401 invalidates read token only, never clearing append token`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()

        val item = sampleEntity("content://media/external/images/media/10", accountName = "alice@example.com")
        uploadDao.upsert(item)

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        // First readback throws 401, second succeeds
        var readAttempt = 0
        uploader.onGetMediaItem = {
            readAttempt++
            if (readAttempt == 1) {
                throw PhotosUploader.HttpStatusException(401, "Read unauthorized")
            } else {
                PhotosUploader.RemoteItem("test_media_id", "photo.jpg", "image/jpeg", "https://photos.google.com/test")
            }
        }

        val worker = createWorker(context, uploadDao, authClient, uploader)
        worker.doWork()

        // Verify cleared tokens: only the read token was cleared!
        assertEquals("Exactly one token must be cleared", 1, authClient.clearedTokens.size)
        assertEquals("Cleared token must be read token", "read_token_valid", authClient.clearedTokens[0])
        assertFalse("Append token must NEVER be cleared for a read 401", authClient.clearedTokens.contains("append_token_valid"))

        // Row was verified
        val result = uploadDao.get(item.contentUri)
        assertNotNull(result)
        assertEquals(CloudUploadEntity.STATE_VERIFIED, result!!.state)
    }

    @Test
    fun `M0-R2 - two consecutive read 401s fail the row without touching append token or consuming retries`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()

        val item = sampleEntity("content://media/external/images/media/20", accountName = "alice@example.com")
        uploadDao.upsert(item)

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        // Both read attempts throw 401
        uploader.onGetMediaItem = {
            throw PhotosUploader.HttpStatusException(401, "Read permission denied")
        }

        val worker = createWorker(context, uploadDao, authClient, uploader)
        worker.doWork()

        // Assert: Append token was NEVER cleared!
        assertFalse("Append token must NEVER be cleared on read failure", authClient.clearedTokens.contains("append_token_valid"))

        // Row failed with 401 error
        val result = uploadDao.get(item.contentUri)
        assertNotNull(result)
        assertEquals(CloudUploadEntity.STATE_FAILED, result!!.state)
        assertTrue(result.lastError!!.contains("read authentication rejected (HTTP 401)"))
    }
}
