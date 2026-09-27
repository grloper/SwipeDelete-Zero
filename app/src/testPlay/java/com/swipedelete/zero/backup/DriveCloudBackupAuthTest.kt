package com.swipedelete.zero.backup

import android.accounts.Account
import android.content.Context
import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.data.local.KeptFileDao
import com.swipedelete.zero.data.local.KeptFileEntity
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.domain.backup.BackupState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * M0-V2-02: Drive cloud backup disconnect, cancellation, and stale-job semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class DriveCloudBackupAuthTest {

    private class InMemoryKeptFileDao : KeptFileDao {
        val kept = mutableListOf<KeptFileEntity>()
        override suspend fun upsert(file: KeptFileEntity) { kept.add(file) }
        override suspend fun remove(uri: String) { kept.removeIf { it.contentUri == uri } }
        override suspend fun pendingBackup(): List<KeptFileEntity> = kept
        override suspend fun pendingDriveBackup(accountId: String): List<KeptFileEntity> = kept
        override fun observePendingDriveBackupCount(accountId: String): Flow<Int> = emptyFlow()
        override fun observePendingBackupCount(): Flow<Int> = emptyFlow()
    }

    private open class InMemoryBackedUpFileDao : BackedUpFileDao {
        val backedUp = mutableListOf<BackedUpFileEntity>()
        override suspend fun insert(file: BackedUpFileEntity) { backedUp.add(file) }
        override fun observeCount(): Flow<Int> = emptyFlow()
        override fun observeBackedUpUris(): Flow<List<String>> = emptyFlow()
        override fun observeAll(): Flow<List<BackedUpFileEntity>> = emptyFlow()
        override suspend fun getAll(): List<BackedUpFileEntity> = backedUp
        override suspend fun get(uri: String): BackedUpFileEntity? = backedUp.firstOrNull { it.contentUri == uri }
        override suspend fun exists(uri: String): Boolean = backedUp.any { it.contentUri == uri }
        override suspend fun delete(uri: String): Int = 0
        override suspend fun deleteAll(): Int = 0
    }

    private class InMemoryCloudUploadDao : CloudUploadDao {
        override fun observeAll(): Flow<List<CloudUploadEntity>> = emptyFlow()
        override suspend fun get(uri: String): CloudUploadEntity? = null
        override suspend fun nextPending(): CloudUploadEntity? = null
        override suspend fun verifiedWithoutLedger(): List<CloudUploadEntity> = emptyList()
        override suspend fun upsert(entity: CloudUploadEntity) {}
        override suspend fun deleteIfQueued(uri: String): Int = 0
        override suspend fun delete(uri: String) {}
        override suspend fun deleteIfCancelable(uri: String): Int = 0
        override suspend fun retryAllFailed(nowMillis: Long): Int = 0
        override suspend fun clearCompleted(): Int = 0
        override fun observeCountByState(state: String): Flow<Int> = emptyFlow()
    }

    private fun sampleKept(uri: String, name: String) = KeptFileEntity(
        contentUri = uri,
        displayName = name,
        mimeType = "image/jpeg",
        sizeBytes = 1024L,
        keptAtMillis = 1000L,
        starred = false,
    )

    @Test
    fun `M0-V2-02 - Drive disconnect between two uploads halts processing and retains SignedOut`() = runTest {
        val context = mock(Context::class.java)
        var accountState: Pair<String?, Account?> = Pair("alice@example.com", Account("alice@example.com", "com.google"))
        val keptDao = InMemoryKeptFileDao()
        val backedUpDao = object : InMemoryBackedUpFileDao() {
            override suspend fun insert(file: BackedUpFileEntity) {
                super.insert(file)
                // Disconnect account right after file 1 is marked backed up in ledger
                accountState = Pair(null, null)
            }
        }
        val uploadDao = InMemoryCloudUploadDao()
        val backupRepo = BackupRepository(keptDao, backedUpDao, uploadDao)

        val file1 = sampleKept("content://media/1", "photo1.jpg")
        val file2 = sampleKept("content://media/2", "photo2.jpg")
        keptDao.upsert(file1)
        keptDao.upsert(file2)

        val driveBackup = DriveCloudBackup(context, backupRepo)
        driveBackup.getSignedInAccount = { accountState }
        driveBackup.getAuthToken = { "valid_drive_token" }
        driveBackup.folderResolver = { "folder_123" }

        val uploadedFiles = mutableListOf<String>()
        driveBackup.fileUploader = { token, folderId, file ->
            uploadedFiles.add(file.contentUri)
            "drive_file_${file.displayName}"
        }

        // Account loss now propagates cancellation rather than an ordinary success.
        var cancelled = false
        try { driveBackup.runBackup() } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue("Disconnect must cancel the owning run", cancelled)

        // Assert: File 1 was uploaded and marked backed up
        assertEquals("File 1 must be uploaded", 1, uploadedFiles.size)
        assertEquals("File 1 uri must match", file1.contentUri, uploadedFiles[0])
        assertTrue("File 1 must be marked backed up in repo", backupRepo.isBackedUp(file1.contentUri))

        // File 2 must NEVER be uploaded or marked backed up
        assertFalse("File 2 must never be uploaded after disconnect", uploadedFiles.contains(file2.contentUri))
        assertFalse("File 2 must never be marked backed up in repo", backupRepo.isBackedUp(file2.contentUri))

        // State must remain SignedOut with disconnect message, NEVER Ready
        val finalState = driveBackup.state.value
        assertTrue("Final state must be SignedOut, got: $finalState", finalState is BackupState.SignedOut)
        assertTrue(finalState is BackupState.SignedOut)
    }

    @Test
    fun `M0-V2-02 - Drive signOut cancels backupJob and retains SignedOut`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val context = mock(Context::class.java)
        val keptDao = InMemoryKeptFileDao()
        val backedUpDao = InMemoryBackedUpFileDao()
        val uploadDao = InMemoryCloudUploadDao()
        val backupRepo = BackupRepository(keptDao, backedUpDao, uploadDao)

        val file1 = sampleKept("content://media/1", "photo1.jpg")
        val file2 = sampleKept("content://media/2", "photo2.jpg")
        keptDao.upsert(file1)
        keptDao.upsert(file2)

        val driveBackup = DriveCloudBackup(context, backupRepo)
        driveBackup.scope = testScope
        var accountState: Pair<String?, Account?> = Pair("alice@example.com", Account("alice@example.com", "com.google"))
        driveBackup.getSignedInAccount = { accountState }
        driveBackup.getAuthToken = { "token" }
        driveBackup.folderResolver = { "folder" }

        var signOutCalledInClient = false
        driveBackup.clientSignOutAction = {
            signOutCalledInClient = true
            accountState = Pair(null, null)
        }

        val uploaded = mutableListOf<String>()
        driveBackup.fileUploader = { _, _, file ->
            uploaded.add(file.contentUri)
            driveBackup.signOut()
            "drive_id"
        }

        driveBackup.backupNow()

        assertTrue("clientSignOutAction must be executed", signOutCalledInClient)
        val state = driveBackup.state.value
        assertTrue("State must remain SignedOut after signOut(), got: $state", state is BackupState.SignedOut)
        // Ledger must have 0 entries because signOut occurred before ledger commit
        assertFalse("No ledger entry when signOut cancels mid-upload", backupRepo.isBackedUp(file1.contentUri))
        assertFalse("No ledger entry for second file", backupRepo.isBackedUp(file2.contentUri))
    }

    @Test
    fun `M0-V3-02 - Auth callback signs out and returns token results in zero folder or upload operations`() = runTest {
        val context = mock(Context::class.java)
        var accountState: Pair<String?, Account?> = Pair("alice@example.com", Account("alice@example.com", "com.google"))
        val keptDao = InMemoryKeptFileDao()
        val backedUpDao = InMemoryBackedUpFileDao()
        val uploadDao = InMemoryCloudUploadDao()
        val backupRepo = BackupRepository(keptDao, backedUpDao, uploadDao)

        val file1 = sampleKept("content://media/1", "photo1.jpg")
        keptDao.upsert(file1)

        val driveBackup = DriveCloudBackup(context, backupRepo)
        driveBackup.getSignedInAccount = { accountState }
        driveBackup.clientSignOutAction = { accountState = Pair(null, null) }

        var folderResolverCalls = 0
        driveBackup.folderResolver = {
            folderResolverCalls++
            "folder_123"
        }

        var fileUploaderCalls = 0
        driveBackup.fileUploader = { _, _, _ ->
            fileUploaderCalls++
            "drive_file_id"
        }

        driveBackup.getAuthToken = {
            // Callback triggers sign-out mid-flight then returns a token
            driveBackup.signOut()
            "stale_token_after_signout"
        }

        var thrown = false
        try {
            driveBackup.runBackup()
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = true
        }

        assertTrue("CancellationException must be thrown when sign-out occurs during auth", thrown)
        assertEquals("folderResolver must never be called after signout", 0, folderResolverCalls)
        assertEquals("fileUploader must never be called after signout", 0, fileUploaderCalls)
        assertFalse("File must never be marked backed up in ledger", backupRepo.isBackedUp(file1.contentUri))
        val state = driveBackup.state.value
        assertTrue("State must remain SignedOut, got: $state", state is BackupState.SignedOut)
    }

    @Test
    fun `M0-V3-02 - Upload callback observes sign-out and throws 401 - no token refresh or reupload after disconnect`() = runTest {
        val context = mock(Context::class.java)
        var accountState: Pair<String?, Account?> = Pair("alice@example.com", Account("alice@example.com", "com.google"))
        val keptDao = InMemoryKeptFileDao()
        val backedUpDao = InMemoryBackedUpFileDao()
        val uploadDao = InMemoryCloudUploadDao()
        val backupRepo = BackupRepository(keptDao, backedUpDao, uploadDao)

        val file1 = sampleKept("content://media/1", "photo1.jpg")
        keptDao.upsert(file1)

        val driveBackup = DriveCloudBackup(context, backupRepo)
        driveBackup.getSignedInAccount = { accountState }
        driveBackup.clientSignOutAction = { accountState = Pair(null, null) }
        driveBackup.folderResolver = { "folder_123" }

        var getAuthTokenCalls = 0
        driveBackup.getAuthToken = {
            getAuthTokenCalls++
            "token_$getAuthTokenCalls"
        }

        var clearAuthTokenCalls = 0
        driveBackup.clearAuthToken = {
            clearAuthTokenCalls++
        }

        var fileUploaderCalls = 0
        driveBackup.fileUploader = { _, _, _ ->
            fileUploaderCalls++
            // Disconnect account and throw 401 HTTP status
            driveBackup.signOut()
            throw DriveCloudBackup.HttpStatusException(401, "Unauthorized")
        }

        var thrown = false
        try {
            driveBackup.runBackup()
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = true
        }

        assertTrue("CancellationException must be thrown on 401 after disconnect", thrown)
        assertEquals("Initial token acquisition should be exactly 1 call", 1, getAuthTokenCalls)
        assertEquals("clearAuthToken must NOT be called after disconnect", 0, clearAuthTokenCalls)
        assertEquals("fileUploader must NOT be called for retry upload", 1, fileUploaderCalls)
        assertFalse("File must never be marked backed up in ledger", backupRepo.isBackedUp(file1.contentUri))
        val state = driveBackup.state.value
        assertTrue("State must remain SignedOut, got: $state", state is BackupState.SignedOut)
    }

    @Test
    fun `M0-V3-02 - Per-file CancellationException is rethrown and not counted as failed file`() = runTest {
        val context = mock(Context::class.java)
        val accountState: Pair<String?, Account?> = Pair("alice@example.com", Account("alice@example.com", "com.google"))
        val keptDao = InMemoryKeptFileDao()
        val backedUpDao = InMemoryBackedUpFileDao()
        val uploadDao = InMemoryCloudUploadDao()
        val backupRepo = BackupRepository(keptDao, backedUpDao, uploadDao)

        val file1 = sampleKept("content://media/1", "photo1.jpg")
        keptDao.upsert(file1)

        val driveBackup = DriveCloudBackup(context, backupRepo)
        driveBackup.getSignedInAccount = { accountState }
        driveBackup.getAuthToken = { "valid_token" }
        driveBackup.folderResolver = { "folder_123" }

        driveBackup.fileUploader = { _, _, _ ->
            throw kotlinx.coroutines.CancellationException("Upload stream aborted")
        }

        var thrown = false
        try {
            driveBackup.runBackup()
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = true
            assertEquals("Upload stream aborted", e.message)
        }

        assertTrue("CancellationException must be rethrown from runBackup", thrown)
        assertFalse("File must not be marked backed up", backupRepo.isBackedUp(file1.contentUri))
        val state = driveBackup.state.value
        assertFalse("State must not be Ready with failed count", state is BackupState.Ready)
        assertTrue("State must be SignedOut on cancellation, got: $state", state is BackupState.SignedOut)
    }

    @Test
    fun `M0-V3-02 - Old job completion cannot clear new session guard or overwrite newer account state`() = runTest {
        val context = mock(Context::class.java)
        val keptDao = InMemoryKeptFileDao()
        val backedUpDao = InMemoryBackedUpFileDao()
        val uploadDao = InMemoryCloudUploadDao()
        val backupRepo = BackupRepository(keptDao, backedUpDao, uploadDao)

        val file1 = sampleKept("content://media/1", "photo1.jpg")
        keptDao.upsert(file1)

        val driveBackup = DriveCloudBackup(context, backupRepo)
        var accountState: Pair<String?, Account?> = Pair("alice@example.com", Account("alice@example.com", "com.google"))
        driveBackup.getSignedInAccount = { accountState }
        driveBackup.getAuthToken = { "alice_token" }
        driveBackup.folderResolver = { "alice_folder" }

        // Session 1 begins
        val session1Id = driveBackup.currentSessionId.incrementAndGet()

        driveBackup.fileUploader = { _, _, _ ->
            // While session 1 is in-flight, a new session (Bob) starts
            driveBackup.currentSessionId.incrementAndGet() // Session 2
            accountState = Pair("bob@example.com", Account("bob@example.com", "com.google"))
            driveBackup.running.set(true)
            "drive_alice_file"
        }

        // Run session 1 directly with session1Id
        try {
            driveBackup.runBackup(session1Id)
        } catch (_: kotlinx.coroutines.CancellationException) {}

        // Assert: Session 1 completion must NOT overwrite state or clear running guard
        assertTrue("running guard must remain true for session 2", driveBackup.running.get())
        assertFalse("Session 1 file must not be committed to ledger after session change", backupRepo.isBackedUp(file1.contentUri))
    }

    @Test
    fun `late auth failure from old launched run cannot sign out reconnected account`() = runTest {
        val kept = InMemoryKeptFileDao()
        kept.upsert(sampleKept("content://media/1", "photo.jpg"))
        val ledger = InMemoryBackedUpFileDao()
        val repo = BackupRepository(kept, ledger, InMemoryCloudUploadDao())
        val backup = DriveCloudBackup(mock(Context::class.java), repo)
        backup.scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        var account = "alice@example.com"
        backup.getSignedInAccount = { account to Account(account, "com.google") }
        var signOuts = 0
        backup.clientSignOutAction = { signOuts++ }
        backup.folderResolver = { "folder" }
        backup.fileUploader = { _, _, _ -> "bob-file" }
        var tokens = 0
        backup.getAuthToken = {
            tokens++
            if (tokens == 1) {
                backup.signOut()
                account = "bob@example.com"
                backup.backupNow()
                throw com.google.android.gms.auth.UserRecoverableAuthException("old consent", android.content.Intent())
            }
            "bob-token"
        }
        backup.backupNow()
        assertEquals(2, tokens)
        assertEquals(1, signOuts)
        assertEquals("bob@example.com", (backup.state.value as BackupState.Ready).accountEmail)
        assertEquals(listOf("bob-file"), ledger.backedUp.map { it.remoteId })
        assertFalse(backup.running.get())
    }

    @Test
    fun `account changes during token acquisition prevent folder and upload requests`() = runTest {
        val kept = InMemoryKeptFileDao()
        kept.upsert(sampleKept("content://media/1", "photo.jpg"))
        val ledger = InMemoryBackedUpFileDao()
        val backup = DriveCloudBackup(mock(Context::class.java), BackupRepository(kept, ledger, InMemoryCloudUploadDao()))
        var account = "alice@example.com"
        backup.getSignedInAccount = { account to Account(account, "com.google") }
        backup.getAuthToken = { account = "bob@example.com"; "old-token" }
        var calls = 0
        backup.folderResolver = { calls++; "folder" }
        backup.fileUploader = { _, _, _ -> calls++; "file" }
        var cancelled = false
        try { backup.runBackup() } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(0, calls)
        assertTrue(ledger.backedUp.isEmpty())
    }

    @Test
    fun `disconnect after folder GET prevents folder POST at production transport boundary`() = runTest {
        val kept = InMemoryKeptFileDao()
        kept.upsert(sampleKept("content://media/1", "photo.jpg"))
        val ledger = InMemoryBackedUpFileDao()
        val backup = DriveCloudBackup(mock(Context::class.java), BackupRepository(kept, ledger, InMemoryCloudUploadDao()))
        backup.getSignedInAccount = { "alice@example.com" to Account("alice@example.com", "com.google") }
        backup.getAuthToken = { "token" }
        backup.clientSignOutAction = {}
        var calls = 0
        backup.connectionFactory = { url ->
            calls++
            object : java.net.HttpURLConnection(java.net.URL(url)) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream(): java.io.InputStream {
                    backup.signOut()
                    return """{"files":[]}""".byteInputStream()
                }
            }
        }
        var cancelled = false
        try { backup.runBackup() } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(1, calls)
        assertTrue(ledger.backedUp.isEmpty())
    }
    private suspend fun exerciseDownloadVerification(
        downloaded: ByteArray,
        downloadCode: Int = 200,
        changeLocal: Boolean = false,
        disconnect: Boolean = false,
    ): Pair<List<BackedUpFileEntity>, List<String>> {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val original = "original-image-bytes".toByteArray()
        val source = java.io.File.createTempFile("drive-backup-", ".jpg", context.cacheDir)
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.swipedelete.zero.data.local.AppDatabase::class.java).build()
        source.writeBytes(original)
        try {
            val kept = InMemoryKeptFileDao()
            kept.upsert(sampleKept(android.net.Uri.fromFile(source).toString(), "fixture.jpg").copy(sizeBytes = original.size.toLong()))
            val ledger = InMemoryBackedUpFileDao()
            val backup = DriveCloudBackup(context, BackupRepository(
                kept, ledger, InMemoryCloudUploadDao(), database.backupReceiptDao(), database,
            ))
            backup.getSignedInAccount = { "alice@example.com" to Account("alice@example.com", "com.google") }
            backup.getAuthToken = { "test-token" }
            backup.folderResolver = { "folder" }
            backup.clientSignOutAction = {}
            val requests = mutableListOf<String>()
            backup.connectionFactory = { url ->
                requests += url
                val isDownload = url.endsWith("?alt=media")
                object : java.net.HttpURLConnection(java.net.URL(url)) {
                    override fun connect() {}
                    override fun disconnect() {}
                    override fun usingProxy() = false
                    override fun getOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()
                    override fun getResponseCode(): Int {
                        assertEquals("Bearer test-token", getRequestProperty("Authorization"))
                        if (isDownload) {
                            assertFalse(useCaches)
                            assertFalse(instanceFollowRedirects)
                        }
                        return if (isDownload) downloadCode else 200
                    }
                    override fun getInputStream(): java.io.InputStream {
                        if (isDownload) {
                            if (changeLocal) source.writeBytes("modified-image-bytes".toByteArray())
                            if (disconnect) backup.signOut()
                            return downloaded.inputStream()
                        }
                        return """{"id":"remote-file"}""".byteInputStream()
                    }
                }
            }
            try { backup.runBackup() } catch (_: kotlinx.coroutines.CancellationException) {
                assertTrue("Only requested disconnection may cancel", disconnect)
            }
            assertTrue("The local original must never be removed", source.exists())
            assertEquals("A remote receipt exists only for successful byte checks",
                ledger.backedUp.isNotEmpty(),
                database.backupReceiptDao().forAccount("GOOGLE_DRIVE", "alice@example.com").isNotEmpty())
            return ledger.backedUp.toList() to requests
        } finally {
            database.close()
            source.delete()
        }
    }

    @Test
    fun `Drive writes receipt only after separate download matches original bytes`() = runTest {
        val (receipts, requests) = exerciseDownloadVerification("original-image-bytes".toByteArray())
        assertEquals(listOf("remote-file"), receipts.map { it.remoteId })
        assertEquals(2, requests.size)
        assertTrue(requests.last().endsWith("/remote-file?alt=media"))
    }

    @Test
    fun `Drive rejects same length corruption and truncated or oversized downloads`() = runTest {
        for (bytes in listOf("corrupt!-image-bytes", "short", "original-image-bytes-extra")) {
            val (receipts, requests) = exerciseDownloadVerification(bytes.toByteArray())
            assertTrue("Corrupt content must not receive a backup receipt", receipts.isEmpty())
            assertEquals(2, requests.size)
        }
    }

    @Test
    fun `Drive download permission failure never creates a backup receipt`() = runTest {
        val (receipts, _) = exerciseDownloadVerification(byteArrayOf(), downloadCode = 403)
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun `Drive rejects original modified during remote verification`() = runTest {
        val (receipts, _) = exerciseDownloadVerification("original-image-bytes".toByteArray(), changeLocal = true)
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun `Drive disconnect during download prevents receipt commit`() = runTest {
        val (receipts, _) = exerciseDownloadVerification("original-image-bytes".toByteArray(), disconnect = true)
        assertTrue(receipts.isEmpty())
    }

    @Test
    fun `Drive clean install inventory and restore verify exact downloaded bytes`() = runTest {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val source = "restore original bytes".toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(source)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val backup = DriveCloudBackup(context, BackupRepository(
            InMemoryKeptFileDao(), InMemoryBackedUpFileDao(), InMemoryCloudUploadDao(),
        ))
        var accountName = "alice@example.com"
        backup.getSignedInAccount = { accountName to Account(accountName, "com.google") }
        backup.getAuthToken = { "token" }
        val urls = mutableListOf<String>()
        backup.connectionFactory = { url ->
            urls += url
            object : java.net.HttpURLConnection(java.net.URL(url)) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream(): java.io.InputStream = when {
                    url.contains("alt=media") -> source.inputStream()
                    url.contains("fields=files(id)") -> """{"files":[{"id":"folder"}]}""".byteInputStream()
                    else -> """{"id":"remote-file","name":"fixture.jpg","mimeType":"image/jpeg",
                        "size":"${source.size}","trashed":false,
                        "appProperties":{"swipeRiseVersion":"1","originalSha256":"$sha","originalSize":"${source.size}"},
                        "files":[{"id":"remote-file","name":"fixture.jpg","mimeType":"image/jpeg",
                        "size":"${source.size}","appProperties":{"swipeRiseVersion":"1",
                        "originalSha256":"$sha","originalSize":"${source.size}"}}]}""".byteInputStream()
                }
            }
        }
        val originals = backup.availableOriginals()
        assertEquals(1, originals.size)
        assertEquals(sha, originals.single().sha256)
        val destination = java.io.File.createTempFile("restore-test", ".jpg", context.cacheDir)
        try {
            assertTrue(backup.restoreOriginal(originals.single(), android.net.Uri.fromFile(destination)))
            assertEquals(source.toList(), destination.readBytes().toList())
            assertTrue(urls.any { it.endsWith("/remote-file?alt=media") })
            accountName = "bob@example.com"
            val previousCalls = urls.size
            assertFalse("Other account cannot restore Alice's record",
                backup.restoreOriginal(originals.single(), android.net.Uri.fromFile(destination)))
            assertEquals(previousCalls, urls.size)
        } finally { destination.delete() }
    }

    @Test
    fun `Drive restore rejects corrupted remote bytes before writing chosen destination`() = runTest {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val original = "original bytes".toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(original)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val backup = DriveCloudBackup(context, BackupRepository(
            InMemoryKeptFileDao(), InMemoryBackedUpFileDao(), InMemoryCloudUploadDao(),
        ))
        backup.getSignedInAccount = { "alice@example.com" to Account("alice@example.com", "com.google") }
        backup.getAuthToken = { "token" }
        backup.connectionFactory = { url ->
            object : java.net.HttpURLConnection(java.net.URL(url)) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream(): java.io.InputStream = if (url.endsWith("alt=media")) {
                    "changed! bytes".byteInputStream()
                } else {
                    """{"id":"remote-file","mimeType":"image/jpeg","size":"${original.size}",
                        "appProperties":{"swipeRiseVersion":"1","originalSha256":"$sha",
                        "originalSize":"${original.size}"}}""".byteInputStream()
                }
            }
        }
        val destination = java.io.File.createTempFile("restore-untouched", ".jpg", context.cacheDir)
        destination.writeText("untouched")
        try {
            val item = com.swipedelete.zero.domain.backup.RemoteOriginal(
                "remote-file", "fixture.jpg", "image/jpeg", original.size.toLong(), sha, "alice@example.com",
            )
            assertFalse(backup.restoreOriginal(item, android.net.Uri.fromFile(destination)))
            assertEquals("untouched", destination.readText())
        } finally { destination.delete() }
    }

    @Test
    fun `large original uses Drive resumable session then exact-byte download`() = runTest {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val original = ByteArray(5 * 1024 * 1024 + 1) { (it % 251).toByte() }
        val file = java.io.File.createTempFile("drive-large-", ".bin", context.cacheDir)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.swipedelete.zero.data.local.AppDatabase::class.java).build()
        file.writeBytes(original)
        try {
            val kept = InMemoryKeptFileDao()
            kept.upsert(sampleKept(android.net.Uri.fromFile(file).toString(), "large.bin")
                .copy(sizeBytes = original.size.toLong(), mimeType = "application/octet-stream"))
            val backup = DriveCloudBackup(context, BackupRepository(kept,
                InMemoryBackedUpFileDao(), InMemoryCloudUploadDao(), db.backupReceiptDao(), db))
            backup.getSignedInAccount = { "alice@example.com" to Account("alice@example.com", "com.google") }
            backup.getAuthToken = { "token" }
            backup.folderResolver = { "folder" }
            val calls = mutableListOf<String>()
            backup.connectionFactory = { url ->
                calls += url
                object : java.net.HttpURLConnection(java.net.URL(url)) {
                    override fun connect() {}
                    override fun disconnect() {}
                    override fun usingProxy() = false
                    override fun getOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()
                    override fun getResponseCode() = if (url.contains("upload_id=abc") &&
                        getRequestProperty("Content-Range") == "bytes */${original.size}") 308 else 200
                    override fun getHeaderField(name: String?): String? =
                        if (name == "Location")
                            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=abc"
                        else null
                    override fun getInputStream(): java.io.InputStream = when {
                        url.contains("/drive/v3/files?q=") -> """{"files":[]}""".byteInputStream()
                        url.contains("alt=media") -> original.inputStream()
                        else -> """{"id":"remote-large"}""".byteInputStream()
                    }
                }
            }
            backup.runBackup()
            assertEquals(5, calls.size)
            assertTrue(calls[0].contains("/drive/v3/files?q="))
            assertTrue(calls[1].contains("uploadType=resumable"))
            assertTrue(calls[2].contains("upload_id=abc"))
            assertTrue(calls[3].contains("upload_id=abc"))
            assertTrue(calls[4].endsWith("remote-large?alt=media"))
            assertEquals(1, db.backupReceiptDao().forAccount("GOOGLE_DRIVE", "alice@example.com").size)
            assertTrue(file.exists())
        } finally { db.close(); file.delete() }
    }

    @Test
    fun `interrupted large upload resumes saved session from server offset without duplicate create`() = runTest {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val original = ByteArray(5 * 1024 * 1024 + 13) { (it % 239).toByte() }
        val file = java.io.File.createTempFile("drive-resume-", ".bin", context.cacheDir)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.swipedelete.zero.data.local.AppDatabase::class.java).build()
        file.writeBytes(original)
        try {
            val kept = InMemoryKeptFileDao()
            kept.upsert(sampleKept(android.net.Uri.fromFile(file).toString(), "resume.bin")
                .copy(sizeBytes = original.size.toLong(), mimeType = "application/octet-stream"))
            val backup = DriveCloudBackup(context, BackupRepository(kept,
                InMemoryBackedUpFileDao(), InMemoryCloudUploadDao(), db.backupReceiptDao(), db))
            backup.getSignedInAccount = { "resume@example.com" to Account("resume@example.com", "com.google") }
            backup.getAuthToken = { "token" }
            backup.folderResolver = { "folder" }
            var starts = 0
            var probes = 0
            var sent = byteArrayOf()
            var sentRange: String? = null
            backup.connectionFactory = { url ->
                object : java.net.HttpURLConnection(java.net.URL(url)) {
                    override fun connect() {}
                    override fun disconnect() {}
                    override fun usingProxy() = false
                    override fun getOutputStream(): java.io.OutputStream = if (
                        url.contains("upload_id=resume") &&
                        getRequestProperty("Content-Range")?.startsWith("bytes 1048576-") == true
                    ) java.io.ByteArrayOutputStream().also { stream ->
                        sentRange = getRequestProperty("Content-Range")
                        sent = byteArrayOf()
                        uploadOutput = stream
                    } else java.io.ByteArrayOutputStream()
                    private var uploadOutput: java.io.ByteArrayOutputStream? = null
                    override fun getResponseCode(): Int = when {
                        url.contains("uploadType=resumable") && !url.contains("upload_id=") -> {
                            starts++; 200
                        }
                        url.contains("upload_id=resume") &&
                            getRequestProperty("Content-Range") == "bytes */${original.size}" -> {
                            probes++
                            if (probes == 1) throw java.io.IOException("lost connection")
                            308
                        }
                        else -> { sent = uploadOutput?.toByteArray() ?: sent; 200 }
                    }
                    override fun getHeaderField(name: String?): String? = when (name) {
                        "Location" -> "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=resume"
                        "Range" -> "bytes=0-1048575"
                        else -> null
                    }
                    override fun getInputStream(): java.io.InputStream = when {
                        url.contains("/drive/v3/files?q=") -> """{"files":[]}""".byteInputStream()
                        url.contains("alt=media") -> original.inputStream()
                        else -> """{"id":"remote-resumed"}""".byteInputStream()
                    }
                }
            }
            backup.runBackup()
            assertEquals(0, db.backupReceiptDao().forAccount("GOOGLE_DRIVE", "resume@example.com").size)
            backup.runBackup()
            assertEquals(1, starts)
            assertEquals(2, probes)
            assertEquals("bytes 1048576-${original.size - 1}/${original.size}", sentRange)
            assertTrue(sent.contentEquals(original.copyOfRange(1048576, original.size)))
            assertEquals(1, db.backupReceiptDao().forAccount("GOOGLE_DRIVE", "resume@example.com").size)
        } finally { db.close(); file.delete() }
    }

    @Test
    fun `completed large upload without local receipt is reconciled by byte download`() = runTest {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val original = ByteArray(5 * 1024 * 1024 + 2) { (it % 197).toByte() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(original)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val file = java.io.File.createTempFile("drive-reconcile-", ".bin", context.cacheDir)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.swipedelete.zero.data.local.AppDatabase::class.java).build()
        file.writeBytes(original)
        try {
            val kept = InMemoryKeptFileDao()
            kept.upsert(sampleKept(android.net.Uri.fromFile(file).toString(), "reconcile.bin")
                .copy(sizeBytes = original.size.toLong(), mimeType = "application/octet-stream"))
            val backup = DriveCloudBackup(context, BackupRepository(kept,
                InMemoryBackedUpFileDao(), InMemoryCloudUploadDao(), db.backupReceiptDao(), db))
            backup.getSignedInAccount = { "reconcile@example.com" to Account("reconcile@example.com", "com.google") }
            backup.getAuthToken = { "token" }
            backup.folderResolver = { "folder" }
            val requests = mutableListOf<String>()
            backup.connectionFactory = { url ->
                requests += url
                object : java.net.HttpURLConnection(java.net.URL(url)) {
                    override fun connect() {}
                    override fun disconnect() {}
                    override fun usingProxy() = false
                    override fun getResponseCode() = 200
                    override fun getInputStream(): java.io.InputStream = if (url.contains("alt=media")) {
                        original.inputStream()
                    } else {
                        """{"files":[{"id":"existing-file","size":"${original.size}","appProperties":{"swipeRiseVersion":"1","originalSize":"${original.size}","originalSha256":"$hash"}}]}""".byteInputStream()
                    }
                }
            }
            backup.runBackup()
            assertEquals(2, requests.size)
            assertTrue(requests[0].contains("/drive/v3/files?q="))
            assertTrue(requests[1].endsWith("existing-file?alt=media"))
            assertEquals("existing-file", db.backupReceiptDao()
                .forAccount("GOOGLE_DRIVE", "reconcile@example.com").single().remoteId)
        } finally { db.close(); file.delete() }
    }

}
