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
import com.swipedelete.zero.domain.backup.UploadReducer
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
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
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
        val querySessionCalls = mutableListOf<String>()

        var onStartSession: (() -> Unit)? = null
        var onUploadChunk: (() -> String?)? = null
        var onGetMediaItem: ((String) -> RemoteItem)? = null
        var querySessionResult: PhotosUploader.SessionQueryResult? = null
        var onQuerySession: ((String) -> PhotosUploader.SessionQueryResult)? = null

        override fun querySession(authToken: String, uploadUrl: String): PhotosUploader.SessionQueryResult {
            querySessionCalls.add(uploadUrl)
            return onQuerySession?.invoke(uploadUrl)
                ?: querySessionResult
                ?: PhotosUploader.SessionQueryResult(0L, "active", null, isResumable = true, isFinal = false)
        }

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
        override suspend fun verifiedWithoutLedger(): List<CloudUploadEntity> =
            map.values.filter { it.state == CloudUploadEntity.STATE_VERIFIED }
        override suspend fun upsert(entity: CloudUploadEntity) { map[entity.contentUri] = entity }
        override suspend fun updateExisting(entity: CloudUploadEntity): Int {
            if (!map.containsKey(entity.contentUri)) return 0
            map[entity.contentUri] = entity; return 1
        }
        override suspend fun deleteIfQueued(uri: String): Int {
            if (map[uri]?.state != CloudUploadEntity.STATE_QUEUED) return 0
            map.remove(uri); return 1
        }
        override suspend fun delete(uri: String) { map.remove(uri) }
        override suspend fun deleteIfCancelable(uri: String): Int {
            if (map[uri]?.state !in listOf(CloudUploadEntity.STATE_QUEUED, CloudUploadEntity.STATE_FAILED)) return 0
            map.remove(uri); return 1
        }
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
        backedUpFileDao: BackedUpFileDao = InMemoryBackedUpFileDao(),
        stagedFileDao: StagedFileDao = InMemoryStagedFileDao(),
    ): PhotosUploadWorker {
        val params = mock(WorkerParameters::class.java)
        val database = mock(AppDatabase::class.java)
        `when`(database.transactionExecutor).thenReturn(java.util.concurrent.Executor { it.run() })
        val worker = PhotosUploadWorker(
            appContext = context,
            params = params,
            uploadDao = uploadDao,
            backedUpFileDao = backedUpFileDao,
            stagedFileDao = stagedFileDao,
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

    @Test fun `queued cancellation after selection wins atomic claim and sends no request`() = runTest {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.cloudUploadDao()
            val item = sampleEntity("content://media/external/images/media/901", "alice@example.com")
            dao.upsert(item)
            val selectedThenCanceled = object : CloudUploadDao by dao {
                override suspend fun nextPending(): CloudUploadEntity? {
                    val selected = dao.nextPending() ?: return null
                    assertEquals(1, dao.deleteIfQueued(selected.contentUri))
                    return selected // The production worker holds the stale selected snapshot.
                }
            }
            val uploader = TestPhotosUploader()
            createWorker(mock(Context::class.java), selectedThenCanceled, TestAuthClient(), uploader,
                db.backedUpFileDao(), db.stagedFileDao()).doWork()
            assertTrue(uploader.startSessionCalls.isEmpty())
            assertTrue(uploader.uploadChunkCalls.isEmpty())
            assertEquals(null, dao.get(item.contentUri))
            assertTrue(db.backedUpFileDao().getAll().isEmpty())
            assertTrue(db.stagedFileDao().getAll().isEmpty())
        } finally { db.close() }
    }

    @Test fun `delayed session start is already claimed so queued Undo cannot cancel running work`() = runTest {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.cloudUploadDao()
            val item = sampleEntity("content://media/external/images/media/902", "alice@example.com")
            dao.upsert(item)
            val context = mock(Context::class.java)
            val resolver = mock(ContentResolver::class.java)
            `when`(context.contentResolver).thenReturn(resolver)
            `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))
            val uploader = TestPhotosUploader()
            uploader.onStartSession = {
                kotlinx.coroutines.runBlocking {
                    assertEquals(CloudUploadEntity.STATE_UPLOADING, dao.get(item.contentUri)?.state)
                    assertEquals(0, dao.deleteIfQueued(item.contentUri))
                    assertEquals(0, dao.deleteIfCancelable(item.contentUri))
                }
            }
            createWorker(context, dao, TestAuthClient(), uploader, db.backedUpFileDao(), db.stagedFileDao()).doWork()
            assertEquals(CloudUploadEntity.STATE_VERIFIED, dao.get(item.contentUri)?.state)
            assertEquals(1, db.backedUpFileDao().getAll().size)
            assertEquals(1, db.stagedFileDao().getAll().size)
        } finally { db.close() }
    }

    @Test fun `actual row removal during delayed session start cannot resurrect send chunks or stage`() = runTest {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.cloudUploadDao()
            val item = sampleEntity("content://media/external/images/media/903", "alice@example.com")
            dao.upsert(item)
            val context = mock(Context::class.java)
            val resolver = mock(ContentResolver::class.java)
            `when`(context.contentResolver).thenReturn(resolver)
            val uploader = TestPhotosUploader()
            // A deterministic gate inside the synchronous transport start: before it returns,
            // another path removes the actual Room row. The old unconditional upsert revived it.
            uploader.onStartSession = { kotlinx.coroutines.runBlocking { dao.delete(item.contentUri) } }
            var stopped = false
            try { createWorker(context, dao, TestAuthClient(), uploader, db.backedUpFileDao(), db.stagedFileDao()).doWork() }
            catch (_: CancellationException) { stopped = true }
            assertTrue("Removed work must stop", stopped)
            assertEquals(null, dao.get(item.contentUri))
            assertTrue(uploader.uploadChunkCalls.isEmpty())
            assertTrue(uploader.batchCreateCalls.isEmpty())
            assertTrue(db.backedUpFileDao().getAll().isEmpty())
            assertTrue(db.stagedFileDao().getAll().isEmpty())
        } finally { db.close() }
    }

    @Test fun `stale persistence cannot overwrite requeued account or generation`() = runTest {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.cloudUploadDao()
            val original = sampleEntity("content://media/external/images/media/904", "alice@example.com")
            dao.upsert(original)
            val claimed = requireNotNull(dao.claimForProcessing(original))
            // Even a replacement within the same millisecond cannot be overwritten:
            // the expected complete row includes the durable claimed state.
            dao.upsert(original)
            assertFalse(dao.updateOwned(claimed.copy(uploadUrl = "https://stale"), claimed))
            assertEquals(original, dao.get(original.contentUri))
            val replacement = original.copy(enqueuedAtMillis = original.enqueuedAtMillis + 1)
            dao.upsert(replacement)
            assertFalse(dao.updateOwned(claimed.copy(uploadUrl = "https://stale"), claimed))
            assertEquals(replacement, dao.get(original.contentUri))
            val otherOwner = original.copy(accountName = "bob@example.com")
            dao.upsert(otherOwner)
            assertFalse(dao.updateOwned(claimed, claimed))
            assertEquals(otherOwner, dao.get(original.contentUri))
        } finally { db.close() }
    }
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

    // =========================================================================
    // M0-V2-02: Disconnect and Cancellation Semantics
    // =========================================================================

    @Test
    fun `M0-V2-02 - disconnect during final readback prevents RemoteVerified save and ledger write`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val item = sampleEntity("content://media/external/images/media/30", accountName = "alice@example.com")
        uploadDao.upsert(item)

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        // Readback succeeds at network level, but account disconnects immediately before returning
        uploader.onGetMediaItem = {
            authClient.activeAccountName = null
            PhotosUploader.RemoteItem("test_media_id", "photo.jpg", "image/jpeg", "https://photos.google.com/test")
        }

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        var thrown = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue("CancellationException must be thrown when account disconnects before verification commit", thrown)

        // Queue state: must NOT be STATE_VERIFIED
        val rowResult = uploadDao.get(item.contentUri)
        assertNotNull(rowResult)
        assertTrue("State must not reach STATE_VERIFIED after disconnect", rowResult!!.state != CloudUploadEntity.STATE_VERIFIED)

        // Success ledger and staging: must be completely empty!
        assertTrue("Ledger must have no entries written after disconnect", backedUpDao.getAll().isEmpty())
        assertTrue("Staged files must have no entries staged after disconnect", stagedDao.getAll().isEmpty())
    }

    @Test
    fun `M0-V2-02 - cancellation during token acquisition is rethrown`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = object : PhotosAuthClient {
            override fun getSignedInAccountName(context: Context): String? = "alice@example.com"
            override fun getSignedInAccount(context: Context): Account? = null
            override fun getToken(context: Context, accountName: String, scope: String): String {
                throw CancellationException("Token acquisition cancelled")
            }
            override fun clearToken(context: Context, token: String) {}
        }
        val uploader = TestPhotosUploader()
        val item = sampleEntity("content://media/external/images/media/31", accountName = "alice@example.com")
        uploadDao.upsert(item)

        val worker = createWorker(context, uploadDao, authClient, uploader)
        var thrown = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            thrown = true
            assertEquals("Token acquisition cancelled", e.message)
        }
        assertTrue("CancellationException during token acquisition must be rethrown", thrown)
    }

    @Test
    fun `M0-V2-02 - startup verifiedWithoutLedger recovery quarantines null or mismatched rows`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        // 1. Legacy null-owner row in STATE_VERIFIED
        val legacyRow = sampleEntity("content://media/external/images/media/50", accountName = null, state = CloudUploadEntity.STATE_VERIFIED)
        uploadDao.upsert(legacyRow)

        // 2. Mismatched account row in STATE_VERIFIED
        val bobRow = sampleEntity("content://media/external/images/media/51", accountName = "bob@example.com", state = CloudUploadEntity.STATE_VERIFIED)
        uploadDao.upsert(bobRow)

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        worker.doWork()

        // Both rows must be quarantined as FAILED
        val legacyResult = uploadDao.get(legacyRow.contentUri)
        assertNotNull(legacyResult)
        assertEquals(CloudUploadEntity.STATE_FAILED, legacyResult!!.state)
        assertTrue(legacyResult.lastError!!.contains("Quarantined: item has unknown account owner"))

        val bobResult = uploadDao.get(bobRow.contentUri)
        assertNotNull(bobResult)
        assertEquals(CloudUploadEntity.STATE_FAILED, bobResult!!.state)
        assertTrue(bobResult.lastError!!.contains("Quarantined: item was authorized under bob@example.com"))

        // Ledger and staging must remain empty
        assertTrue("No ledger writes for quarantined legacy/mismatched rows", backedUpDao.getAll().isEmpty())
        assertTrue("No staging writes for quarantined legacy/mismatched rows", stagedDao.getAll().isEmpty())
    }

    @Test
    fun `M0-V2-02 - startup verifiedWithoutLedger recovery aborts on account disconnect`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val verifiedRow = sampleEntity("content://media/external/images/media/60", accountName = "alice@example.com", state = CloudUploadEntity.STATE_VERIFIED)
        uploadDao.upsert(verifiedRow)

        // Disconnect account right when startup recovery checks it
        authClient.activeAccountName = null

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        worker.doWork()

        // Worker returns without modifying ledger
        assertTrue("Ledger must NOT receive entries when account is disconnected", backedUpDao.getAll().isEmpty())
        assertTrue("Staged files must NOT receive entries when account is disconnected", stagedDao.getAll().isEmpty())
    }

    @Test
    fun `M0-V3-02 - Photos token acquisition observes disconnect before readback - no following media request or success persistence`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val authClient = object : PhotosAuthClient {
            var activeAccount: String? = "alice@example.com"
            val requestedScopes = mutableListOf<String>()

            override fun getSignedInAccountName(context: Context): String? = activeAccount
            override fun getSignedInAccount(context: Context): Account? = null
            override fun getToken(context: Context, accountName: String, scope: String): String {
                requestedScopes.add(scope)
                if (scope.contains(PhotosUploader.PHOTOS_READ_SCOPE)) {
                    // Sign out right during read token acquisition!
                    activeAccount = null
                    return "stale_read_token"
                }
                return "valid_append_token"
            }
            override fun clearToken(context: Context, token: String) {}
        }

        val uploader = TestPhotosUploader()
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        val item = sampleEntity("content://media/external/images/media/42", accountName = "alice@example.com")
        uploadDao.upsert(item)

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)

        var thrown = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            thrown = true
        }

        assertTrue("CancellationException must be thrown on disconnect during read token acquisition", thrown)
        assertEquals("uploader.getMediaItem must never be called after disconnect", 0, uploader.getMediaItemCalls.size)
        assertTrue("Ledger must have no entries", backedUpDao.getAll().isEmpty())
        assertTrue("Staged files must have no entries", stagedDao.getAll().isEmpty())
        val saved = uploadDao.get(item.contentUri)
        assertNotNull(saved)
        assertFalse("Item must NOT be marked VERIFIED", saved!!.state == CloudUploadEntity.STATE_VERIFIED)
    }

    // =========================================================================
    // M0-V3-03: Session Recovery Full Orchestrator & HTTP Boundary Tests
    // =========================================================================

    @Test
    fun `M0-V3-03 - session recovery with active partial offset resumes upload from reported offset`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        // Pre-existing session in UPLOADING state with uploadUrl
        val existingRow = sampleEntity("content://media/external/images/media/70", accountName = "alice@example.com", state = CloudUploadEntity.STATE_UPLOADING).copy(
            uploadUrl = "https://upload.google.com/sessions/resumable_123",
            bytesUploaded = 0L,
            sizeBytes = 100L,
        )
        uploadDao.upsert(existingRow)

        // Query session returns active partial offset of 40 bytes
        uploader.querySessionResult = PhotosUploader.SessionQueryResult(
            offset = 40L,
            status = "active",
            uploadToken = null,
            isResumable = true,
            isFinal = false,
        )

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals("querySession must be called for existing uploadUrl", 1, uploader.querySessionCalls.size)
        assertEquals("existing uploadUrl must be queried", "https://upload.google.com/sessions/resumable_123", uploader.querySessionCalls[0])
        assertEquals("startSession must NOT be called for resumable active session", 0, uploader.startSessionCalls.size)
        assertTrue("Upload chunk must resume from offset 40", uploader.uploadChunkCalls.contains(40L))
        assertEquals("First chunk offset must be 40", 40L, uploader.uploadChunkCalls[0])
        assertEquals(1, backedUpDao.getAll().size)
    }

    @Test
    fun `M0-V3-03 - session recovery with full or out-of-range offset resets session and restarts from byte 0`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        val existingRow = sampleEntity("content://media/external/images/media/71", accountName = "alice@example.com", state = CloudUploadEntity.STATE_UPLOADING).copy(
            uploadUrl = "https://upload.google.com/sessions/resumable_out_of_range",
            bytesUploaded = 50L,
            sizeBytes = 100L,
        )
        uploadDao.upsert(existingRow)

        // Query session returns offset 100 (>= sizeBytes) but isFinal is false (token missing)
        uploader.querySessionResult = PhotosUploader.SessionQueryResult(
            offset = 100L,
            status = "active",
            uploadToken = null,
            isResumable = true,
            isFinal = false,
        )

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, uploader.querySessionCalls.size)
        assertEquals("startSession must be called to reset out-of-range unfinalized session", 1, uploader.startSessionCalls.size)
        assertEquals("Upload chunk must restart from byte 0", 0L, uploader.uploadChunkCalls[0])
    }

    @Test
    fun `M0-V3-03 - session recovery with lost completion receipt recovers finalized upload token`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        val existingRow = sampleEntity("content://media/external/images/media/72", accountName = "alice@example.com", state = CloudUploadEntity.STATE_UPLOADING).copy(
            uploadUrl = "https://upload.google.com/sessions/finalized_url",
            bytesUploaded = 100L,
            sizeBytes = 100L,
        )
        uploadDao.upsert(existingRow)

        // Query returns final status with uploadToken
        uploader.querySessionResult = PhotosUploader.SessionQueryResult(
            offset = 100L,
            status = "final",
            uploadToken = "recovered_upload_token_999",
            isResumable = false,
            isFinal = true,
        )

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, uploader.querySessionCalls.size)
        assertEquals("No chunk uploads needed when final token is recovered", 0, uploader.uploadChunkCalls.size)
        assertEquals(1, uploader.batchCreateCalls.size)
        assertEquals("recovered_upload_token_999", uploader.batchCreateCalls[0])
        assertEquals(1, backedUpDao.getAll().size)
    }

    @Test
    fun `M0-V3-03 - session recovery with final status but lost token resets session and restarts from byte 0`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        val existingRow = sampleEntity("content://media/external/images/media/73", accountName = "alice@example.com", state = CloudUploadEntity.STATE_UPLOADING).copy(
            uploadUrl = "https://upload.google.com/sessions/lost_token_url",
            bytesUploaded = 100L,
            sizeBytes = 100L,
        )
        uploadDao.upsert(existingRow)

        // Final status but upload token is lost (null)
        uploader.querySessionResult = PhotosUploader.SessionQueryResult(
            offset = 100L,
            status = "final",
            uploadToken = null,
            isResumable = false,
            isFinal = true,
        )

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, uploader.querySessionCalls.size)
        assertEquals("startSession must be called when token is lost from final session", 1, uploader.startSessionCalls.size)
        assertEquals("Upload chunk must restart from byte 0", 0L, uploader.uploadChunkCalls[0])
    }

    @Test
    fun `M0-V3-03 - session recovery encountering HTTP 404 or 410 expired session resets session and restarts from byte 0`() = runTest {
        val context = mock(Context::class.java)
        val uploadDao = InMemoryCloudUploadDao()
        val authClient = TestAuthClient(activeAccountName = "alice@example.com")
        val uploader = TestPhotosUploader()
        val backedUpDao = InMemoryBackedUpFileDao()
        val stagedDao = InMemoryStagedFileDao()

        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any())).thenReturn(ByteArrayInputStream(ByteArray(100)))

        val existingRow = sampleEntity("content://media/external/images/media/74", accountName = "alice@example.com", state = CloudUploadEntity.STATE_UPLOADING).copy(
            uploadUrl = "https://upload.google.com/sessions/expired_url",
            bytesUploaded = 50L,
            sizeBytes = 100L,
        )
        uploadDao.upsert(existingRow)

        // querySession throws HTTP 404 (session expired)
        uploader.onQuerySession = { _ ->
            throw PhotosUploader.HttpStatusException(404, "Session Expired")
        }

        val worker = createWorker(context, uploadDao, authClient, uploader, backedUpDao, stagedDao)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, uploader.querySessionCalls.size)
        assertEquals("startSession must be called after 404 expired session", 1, uploader.startSessionCalls.size)
        assertEquals("Upload chunk must restart from byte 0", 0L, uploader.uploadChunkCalls[0])
    }

    private class FixtureConnection(
        url: String, private val status: Int = 200,
        private val headers: Map<String, String> = emptyMap(), private val body: String = "",
        private val beforeResponse: () -> Unit = {},
    ) : java.net.HttpURLConnection(java.net.URL(url)) {
        val sent = java.io.ByteArrayOutputStream()
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream(): java.io.OutputStream = sent
        override fun getInputStream(): java.io.InputStream = body.byteInputStream()
        override fun getErrorStream(): java.io.InputStream = body.byteInputStream()
        override fun getResponseCode(): Int { beforeResponse(); return status }
        override fun getHeaderField(name: String): String? = headers[name]
    }

    @Test
    fun `production HTTP recovery parses raw responses and bounds requests`() = runTest {
        val scenarios = listOf(
            Triple("active", "40", 200), Triple("active", "100", 200),
            Triple("active", "101", 200), Triple("final", "100", 200),
            Triple("terminated", "20", 200), Triple("active", "bad", 200),
            Triple("active", "-1", 200), Triple("", "", 200),
            Triple("expired", "0", 404), Triple("expired", "0", 410),
        )
        for ((status, offset, http) in scenarios) {
            val context = mock(Context::class.java)
            val resolver = mock(ContentResolver::class.java)
            `when`(context.contentResolver).thenReturn(resolver)
            `when`(resolver.openInputStream(org.mockito.ArgumentMatchers.any()))
                .thenAnswer { ByteArrayInputStream(ByteArray(100)) }
            val queue = InMemoryCloudUploadDao()
            val ledger = InMemoryBackedUpFileDao()
            val staging = InMemoryStagedFileDao()
            val auth = TestAuthClient()
            queue.upsert(sampleEntity("content://media/external/images/media/99", "alice@example.com",
                CloudUploadEntity.STATE_UPLOADING).copy(uploadUrl = "https://photoslibrary.googleapis.com/v1/uploads?upload_id=fixture-session&upload_protocol=resumable", sizeBytes = 100))
            val calls = mutableListOf<FixtureConnection>()
            val uploader = PhotosUploader()
            val resumes = status == "active" && offset == "40" && http == 200
            uploader.connectionFactory = { url ->
                val connection = when (calls.size) {
                    0 -> FixtureConnection(url, http, mapOf("X-Goog-Upload-Status" to status,
                        "X-Goog-Upload-Size-Received" to offset), "untrusted-query-body")
                    else -> when {
                        url.endsWith("/v1/uploads") -> FixtureConnection(url, headers = mapOf(
                            "X-Goog-Upload-URL" to "https://photoslibrary.googleapis.com/v1/uploads?upload_id=fixture-fresh&upload_protocol=resumable", "X-Goog-Upload-Chunk-Granularity" to "1"))
                        url.contains("batchCreate") -> FixtureConnection(url, body = """{"newMediaItemResults":[{"mediaItem":{"id":"test_media_id"}}]}""")
                        url.contains("/mediaItems/") -> FixtureConnection(url, body = """{"id":"test_media_id","filename":"photo.jpg","mimeType":"image/jpeg","baseUrl":"https://fixture.test/image","productUrl":"https://photos.google.com/test"}""")
                        else -> FixtureConnection(url, body = "fixture-finalize-receipt")
                    }
                }
                calls.add(connection); connection
            }
            assertEquals(androidx.work.ListenableWorker.Result.success(), createWorker(context, queue, auth, uploader, ledger, staging).doWork())
            assertEquals("$status/$offset/$http", if (resumes) 4 else 5, calls.size)
            assertEquals("query", calls.first().getRequestProperty("X-Goog-Upload-Command"))
            val chunk = calls.single { it.getRequestProperty("X-Goog-Upload-Command") == "upload, finalize" }
            assertEquals(if (resumes) "40" else "0", chunk.getRequestProperty("X-Goog-Upload-Offset"))
            assertEquals(if (resumes) 60 else 100, chunk.sent.size())
            assertTrue(calls.all { it.closed })
            assertEquals(1, ledger.ledger.size)
            assertEquals(1, staging.staged.size)
            assertTrue(calls.single { it.url.toString().contains("batchCreate") }.sent.toString().contains("fixture-finalize-receipt"))
        }
    }

    @Test
    fun `production HTTP recovery cancellation prevents subsequent request and success writes`() = runTest {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        val queue = InMemoryCloudUploadDao()
        val ledger = InMemoryBackedUpFileDao()
        val staging = InMemoryStagedFileDao()
        val auth = TestAuthClient()
        queue.upsert(sampleEntity("content://media/external/images/media/99", "alice@example.com",
            CloudUploadEntity.STATE_UPLOADING).copy(uploadUrl = "https://photoslibrary.googleapis.com/v1/uploads?upload_id=fixture-session&upload_protocol=resumable", sizeBytes = 100))
        var calls = 0
        val uploader = PhotosUploader()
        uploader.connectionFactory = { url ->
            calls++
            FixtureConnection(url, headers = mapOf("X-Goog-Upload-Status" to "terminated"),
                beforeResponse = { auth.activeAccountName = null })
        }
        var cancelled = false
        try { createWorker(context, queue, auth, uploader, ledger, staging).doWork() }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(1, calls)
        assertTrue(ledger.ledger.isEmpty())
        assertTrue(staging.staged.isEmpty())
    }

    @Test
    fun `production HTTP recovery stops after bounded server failures`() = runTest {
        val context = mock(Context::class.java)
        `when`(context.contentResolver).thenReturn(mock(ContentResolver::class.java))
        val queue = InMemoryCloudUploadDao()
        val ledger = InMemoryBackedUpFileDao()
        val staging = InMemoryStagedFileDao()
        val auth = TestAuthClient()
        val uri = "content://media/external/images/media/99"
        queue.upsert(sampleEntity(uri, "alice@example.com", CloudUploadEntity.STATE_UPLOADING)
            .copy(uploadUrl = "https://photoslibrary.googleapis.com/v1/uploads?upload_id=fixture-session&upload_protocol=resumable", sizeBytes = 100))
        var calls = 0
        val uploader = PhotosUploader()
        uploader.connectionFactory = { url -> calls++; FixtureConnection(url, status = 503) }
        repeat(UploadReducer.MAX_ATTEMPTS + 1) {
            createWorker(context, queue, auth, uploader, ledger, staging).doWork()
        }
        assertEquals(UploadReducer.MAX_ATTEMPTS, calls)
        assertEquals(CloudUploadEntity.STATE_FAILED, queue.get(uri)!!.state)
        assertTrue(ledger.ledger.isEmpty())
        assertTrue(staging.staged.isEmpty())
    }
}

