package com.swipedelete.zero

import android.content.Context
import android.content.Intent
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.data.repository.PurgeEngine
import com.swipedelete.zero.data.repository.SafStorageBridge
import com.swipedelete.zero.data.repository.StoragePermissionManager
import com.swipedelete.zero.domain.backup.ArchiveItemState
import com.swipedelete.zero.domain.backup.CloudUploadStats
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Regression and safety tests for M0:
 * SAFE-01: Default-deny deletion containment across all states in Play/cloud.
 * SAFE-02: Mixed selections with one invalid proof cause zero mutations for that batch.
 * SAFE-04: Same-size mutation risk: containment vs true M1 restore-verification fix.
 */
class M0SafetyContainmentTest {

    private fun item(id: String, sizeBytes: Long = 1024L) = StagedFileEntity(
        contentUri = "content://media/external/images/media/$id",
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        mediaType = "IMAGE",
        sizeBytes = sizeBytes,
        relativePath = null,
        stagedAtMillis = System.currentTimeMillis(),
        sourceDeckId = null,
    )

    private fun fakeArchive(
        isAvailable: Boolean = true,
        check: (StagedFileEntity) -> Boolean = { true },
    ) = object : PhotosArchive {
        val checked = mutableListOf<String>()
        override val isAvailable: Boolean = isAvailable
        override val queue: Flow<Map<String, ArchiveItemState>> = MutableStateFlow(emptyMap())
        override val uploadStats: Flow<CloudUploadStats> = MutableStateFlow(CloudUploadStats())
        override suspend fun enqueue(item: MediaItem) = Unit
        override suspend fun enqueueStaged(item: StagedFileEntity) = Unit
        override suspend fun verifyRemote(item: StagedFileEntity): Boolean {
            checked += item.displayName
            return check(item)
        }
        override suspend fun remoteUrl(remoteId: String): String? = null
        override suspend fun cancelIfQueued(contentUri: String) = Unit
        override suspend fun cancel(contentUri: String) = Unit
        override fun retry(contentUri: String) = Unit
        override fun retryAllFailed() = Unit
        override fun clearFinished() = Unit
        override suspend fun rebackup(item: MediaItem) = Unit
        override fun openInPhotosIntent(): Intent? = null
    }

    // =========================================================================
    // SAFE-01: Central Non-Destructive Safety Lock & Default Deny
    // =========================================================================

    @Test
    fun `SAFE-01 - metadata-only Google Photos verification cannot authorize local deletion in M0`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        // Archive returns true for remote metadata readiness, but M0 domain lock must still deny.
        val archive = fakeArchive(isAvailable = true) { true }
        val engine = PurgeEngine(
            mock(Context::class.java), mediaStore, safBridge,
            mock(StoragePermissionManager::class.java), archive,
        )

        val plan = engine.preparePurge(
            listOf(item("photo_1"), item("photo_2")),
            ExecutionMode.PERMANENT_PURGE,
        )

        assertTrue("Plan must fail under M0 lock", plan is PurgeEngine.PurgePlan.Failed)
        val failureReason = (plan as PurgeEngine.PurgePlan.Failed).reason
        assertTrue("Failure reason must cite M0 safety containment", failureReason.contains("M0 safety containment"))
        assertTrue("Failure reason must explain original-byte restoration requirement", failureReason.contains("original-byte restoration"))

        // Critical safety verification: zero destructive calls made
        verifyNoInteractions(mediaStore, safBridge)
    }

    @Test
    fun `SAFE-01 - metadata-only verification also blocks Android 30-day Trash in M0`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        val archive = fakeArchive(isAvailable = true) { true }
        val engine = PurgeEngine(
            mock(Context::class.java), mediaStore, safBridge,
            mock(StoragePermissionManager::class.java), archive,
        )

        val plan = engine.preparePurge(
            listOf(item("photo_trash")),
            ExecutionMode.OS_TRASH_30_DAY,
        )

        assertTrue(plan is PurgeEngine.PurgePlan.Failed)
        assertTrue((plan as PurgeEngine.PurgePlan.Failed).reason.contains("M0 safety containment"))
        verifyNoInteractions(mediaStore, safBridge)
    }

    @Test
    fun `SAFE-01 - unavailable or signed-out backup provider in cloud product blocks deletion`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        // Provider is unavailable (e.g. offline, signed out, unconfigured)
        val archive = fakeArchive(isAvailable = false)
        val engine = PurgeEngine(
            mock(Context::class.java), mediaStore, safBridge,
            mock(StoragePermissionManager::class.java), archive,
        )

        val eligibility = engine.checkDeletionEligibility(listOf(item("offline_file")))
        // In Play/cloud build with SUPPORTS_PHOTOS_ARCHIVE, unavailable provider blocks deletion
        if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE) {
            assertTrue("Unavailable provider must be blocked", eligibility is PurgeEngine.DeletionEligibility.Blocked)
        }
        verifyNoInteractions(mediaStore, safBridge)
    }

    // =========================================================================
    // SAFE-02: Mixed Selection Protection
    // =========================================================================

    @Test
    fun `SAFE-02 - mixed batch with one unverified file blocks the entire batch with zero mutations`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        val archive = fakeArchive(isAvailable = true) { it.displayName != "unverified.jpg" }
        val engine = PurgeEngine(
            mock(Context::class.java), mediaStore, safBridge,
            mock(StoragePermissionManager::class.java), archive,
        )

        val items = listOf(
            item("verified_1"),
            item("unverified"),
            item("verified_2"),
        )

        val plan = engine.preparePurge(items, ExecutionMode.PERMANENT_PURGE)
        assertTrue(plan is PurgeEngine.PurgePlan.Failed)
        val reason = (plan as PurgeEngine.PurgePlan.Failed).reason
        assertTrue("Must identify unverified file", reason.contains("unverified.jpg"))
        assertTrue("Must guide user to wait for verification", reason.contains("wait for verification"))

        // Absolutely zero mutation allowed on the verified subset
        verifyNoInteractions(mediaStore, safBridge)
    }

    // =========================================================================
    // SAFE-04: Same-Size Mutation Vulnerability Containment vs M1 Fix
    // =========================================================================

    @Test
    fun `SAFE-04 - same-size edited content at same URI is contained by M0 safety lock`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        // Simulation of the P1 vulnerability:
        // Local file bytes were edited from A to B while preserving name and byte size.
        // A naive metadata check would see: same URI, same size (1024), same name ("edited.jpg").
        // Therefore fakeArchive (representing Google Photos metadata check) reports true.
        val naiveMetadataArchive = fakeArchive(isAvailable = true) { staged ->
            staged.sizeBytes == 1024L && staged.displayName == "edited.jpg"
        }

        val engine = PurgeEngine(
            mock(Context::class.java), mediaStore, safBridge,
            mock(StoragePermissionManager::class.java), naiveMetadataArchive,
        )

        // The edited item
        val editedItem = item("edited", sizeBytes = 1024L)

        // Under M0 safety containment:
        // Even though naive metadata check passes, PurgeEngine enforces the domain lock,
        // preventing the modified local file from being deleted without M1 restore proof.
        val plan = engine.preparePurge(listOf(editedItem), ExecutionMode.PERMANENT_PURGE)

        assertTrue("M0 lock must contain same-size mutation risk", plan is PurgeEngine.PurgePlan.Failed)
        assertTrue(
            "Must explain M0 containment vs M1 byte-for-byte restore requirement",
            (plan as PurgeEngine.PurgePlan.Failed).reason.contains("M0 safety containment")
        )

        // Verify zero disk/MediaStore mutations
        verifyNoInteractions(mediaStore, safBridge)
    }
}
