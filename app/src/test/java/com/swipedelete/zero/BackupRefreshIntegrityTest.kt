package com.swipedelete.zero

import androidx.room.Room
import com.swipedelete.zero.data.local.*
import com.swipedelete.zero.data.repository.BackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRefreshIntegrityTest {
    private val uri = "content://media/external/images/media/101"
    private fun receipt(uri: String = this.uri, account: String = "alice@example.com", provider: String = "GOOGLE_DRIVE") =
        BackupReceiptEntity(uri, provider, account, "remote", "a".repeat(64), 10, "fixture.png", "image/png", 1)
    private fun repository(db: AppDatabase) = BackupRepository(db.keptFileDao(), db.backedUpFileDao(), db.cloudUploadDao(), db.backupReceiptDao(), db)
    private suspend fun kept(db: AppDatabase) = db.keptFileDao().upsert(KeptFileEntity(uri, "fixture.png", "image/png", 10, 1, false))

    @Test fun reuploadBindsSelectedAccountAndMissingAccountCannotEraseHistory() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val repo = repository(db)
            db.backedUpFileDao().insert(BackedUpFileEntity(uri, 10, "photos:old", 1))
            for (account in listOf(null, "", "  ")) {
                try { repo.rebackup(uri, "fixture.png", "image/png", 10, account); fail("Missing owner accepted") }
                catch (_: IllegalArgumentException) { }
                assertEquals("photos:old", db.backedUpFileDao().get(uri)?.remoteId)
                assertTrue(db.cloudUploadDao().get(uri) == null)
            }
            repo.rebackup(uri, "fixture.png", "image/png", 10, "alice@example.com")
            assertEquals("alice@example.com", db.cloudUploadDao().get(uri)?.accountName)
            assertEquals(CloudUploadEntity.STATE_QUEUED, db.cloudUploadDao().get(uri)?.state)
            assertNull(db.backedUpFileDao().get(uri))
            db.backedUpFileDao().insert(BackedUpFileEntity(uri, 10, "drive-original", 1))
            db.backupReceiptDao().upsert(receipt())
            repo.rebackup(uri, "fixture.png", "image/png", 10, "bob@example.com")
            assertEquals("bob@example.com", db.cloudUploadDao().get(uri)?.accountName)
            assertEquals("drive-original", db.backedUpFileDao().get(uri)?.remoteId)
            assertNotNull(db.backupReceiptDao().get(uri, "GOOGLE_DRIVE", "alice@example.com"))
        } finally { db.close() }
    }

    @Test fun explicitUriForgetRemovesAllLocalReceiptsAndMakesDriveWorkPending() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val repo = repository(db)
            kept(db)
            db.backupReceiptDao().upsert(receipt())
            db.backupReceiptDao().upsert(receipt(account = "bob@example.com"))
            db.backupReceiptDao().upsert(receipt(provider = "ICLOUD"))
            val other = "$uri-other"
            db.backupReceiptDao().upsert(receipt(uri = other))
            assertTrue(repo.pendingDriveBackup("alice@example.com").isEmpty())
            assertTrue(repo.forgetBackedUp(uri)) // Receipt-only history is still a real removal.
            assertEquals(listOf(uri), repo.pendingDriveBackup("alice@example.com").map { it.contentUri })
            assertNull(db.backupReceiptDao().get(uri, "GOOGLE_DRIVE", "bob@example.com"))
            assertNull(db.backupReceiptDao().get(uri, "ICLOUD", "alice@example.com"))
            assertNotNull(db.backupReceiptDao().get(other, "GOOGLE_DRIVE", "alice@example.com"))
            assertFalse(repo.forgetBackedUp(uri))
        } finally { db.close() }
    }

    @Test fun explicitRecheckInvalidatesOnlySelectedDriveAccountEvenWhenSizeUnchanged() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val repo = repository(db)
            kept(db)
            db.backupReceiptDao().upsert(receipt())
            db.backupReceiptDao().upsert(receipt(account = "bob@example.com"))
            db.backupReceiptDao().upsert(receipt(provider = "ICLOUD"))
            assertTrue(repo.pendingDriveBackup("alice@example.com").isEmpty())
            assertEquals(1, repo.invalidateDriveReceipts("alice@example.com"))
            assertEquals(listOf(uri), repo.pendingDriveBackup("alice@example.com").map { it.contentUri })
            assertTrue(repo.pendingDriveBackup("bob@example.com").isEmpty())
            assertNotNull(db.backupReceiptDao().get(uri, "ICLOUD", "alice@example.com"))
            assertEquals(0, repo.invalidateDriveReceipts("alice@example.com"))
        } finally { db.close() }
    }
}
