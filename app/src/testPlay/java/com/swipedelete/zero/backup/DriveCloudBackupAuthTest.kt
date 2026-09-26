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
class DriveCloudBackupAuthTest {

    private class InMemoryKeptFileDao : KeptFileDao {
        val kept = mutableListOf<KeptFileEntity>()
        override suspend fun upsert(file: KeptFileEntity) { kept.add(file) }
        override suspend fun remove(uri: String) { kept.removeIf { it.contentUri == uri } }
        override suspend fun pendingBackup(): List<KeptFileEntity> = kept
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

        // Run backup synchronously
        driveBackup.runBackup()

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
        assertEquals("Account disconnected during backup.", (finalState as BackupState.SignedOut).message)
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
}
