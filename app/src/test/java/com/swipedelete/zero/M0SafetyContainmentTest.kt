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
import com.swipedelete.zero.ui.screens.staging.StagingUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * Regression and safety tests for M0:
 * SAFE-01: Default-deny deletion containment across all states in every edition.
 * SAFE-02: Throwing provider fake is NEVER touched by an M0 delete request.
 * SAFE-03: Staging UI eligibility reflects disabled cleanup and product explanation.
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

    // =========================================================================
    // SAFE-01 & SAFE-02: Central Non-Destructive Safety Lock & Throwing Fake Isolation
    // =========================================================================

    @Test
    fun `SAFE-01 - throwing provider fake is never touched by an M0 delete request`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        // Provider fake that throws if ANY method is called
        val throwingArchive = object : PhotosArchive {
            override val isAvailable: Boolean get() = error("Provider fake must never be touched in M0")
            override val queue: Flow<Map<String, ArchiveItemState>> get() = error("Provider fake must never be touched in M0")
            override val uploadStats: Flow<CloudUploadStats> get() = error("Provider fake must never be touched in M0")
            override suspend fun enqueue(item: MediaItem) = error("Provider fake must never be touched in M0")
            override suspend fun enqueueStaged(item: StagedFileEntity) = error("Provider fake must never be touched in M0")
            override suspend fun verifyRemote(item: StagedFileEntity): Boolean = error("verifyRemote must never be touched in M0")
            override suspend fun remoteUrl(remoteId: String): String? = null
            override suspend fun cancelIfQueued(contentUri: String) = Unit
            override suspend fun cancel(contentUri: String) = Unit
            override fun retry(contentUri: String) = Unit
            override fun retryAllFailed() = Unit
            override fun clearFinished() = Unit
            override suspend fun rebackup(item: MediaItem) = Unit
            override fun openInPhotosIntent(): Intent? = null
        }

        val context = mock(Context::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val engine = PurgeEngine(context, mediaStore, safBridge, permissions, throwingArchive)
        engine.requestBuilder = { _, _ -> error("OS confirmation must not be created while locked") }

        // PERMANENT_PURGE must fail immediately with product explanation
        val permanentPlan = engine.preparePurge(
            listOf(item("photo_1"), item("photo_2")),
            ExecutionMode.PERMANENT_PURGE,
        )
        assertTrue("Permanent purge must fail under M0 lock", permanentPlan is PurgeEngine.PurgePlan.Failed)
        val permanentReason = (permanentPlan as PurgeEngine.PurgePlan.Failed).reason
        assertEquals(
            "Cleanup is unavailable in this test build. Your originals stay on this device.",
            permanentReason,
        )

        // OS_TRASH_30_DAY must also fail immediately with product explanation
        val trashPlan = engine.preparePurge(
            listOf(item("photo_trash")),
            ExecutionMode.OS_TRASH_30_DAY,
        )
        assertTrue("Trash mode must fail under M0 lock", trashPlan is PurgeEngine.PurgePlan.Failed)
        val trashReason = (trashPlan as PurgeEngine.PurgePlan.Failed).reason
        assertEquals(
            "Cleanup is unavailable in this test build. Your originals stay on this device.",
            trashReason,
        )

        // Documents would otherwise use direct-file or SAF deletion; Android 10
        // images would otherwise take the legacy ContentResolver deletion path.
        val document = item("document").copy(mediaType = "DOCUMENT", mimeType = "application/pdf", relativePath = "/synthetic/document.pdf")
        for (sdk in listOf(29, 30, 36)) {
            engine.sdkInt = sdk
            for (mode in ExecutionMode.entries) {
                val batch = listOf(item("image"), document)
                assertTrue(engine.checkDeletionEligibility(batch) is PurgeEngine.DeletionEligibility.Blocked)
                repeat(2) {
                    assertTrue(engine.preparePurge(batch, mode) is PurgeEngine.PurgePlan.Failed)
                }
            }
        }

        // Critical safety verification: zero destructive calls made
        verifyNoInteractions(context, mediaStore, safBridge, permissions)
    }

    // =========================================================================
    // SAFE-03: UI Eligibility and Product Language Consistency
    // =========================================================================

    @Test
    fun `SAFE-03 - cleanup stays disabled with or without backup requirements in every edition`() {
        for (backupRequired in listOf(false, true)) {
            val state = StagingUiState(
                items = listOf(item("photo_1")), backupRequired = backupRequired,
                backupConnected = true, pendingBackupCount = 0, verifiedCount = 1,
            )
            assertFalse(state.cleanupAvailable)
            assertFalse(state.canDelete)
            assertEquals(PurgeEngine.M0_SAFETY_LOCK_MESSAGE, state.cleanupLockExplanation)
        }
    }

    @Test
    fun `empty queues are harmless and touch no storage or provider`() = runTest {
        val context = mock(Context::class.java)
        val media = mock(MediaStoreRepository::class.java)
        val saf = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val archive = mock(PhotosArchive::class.java)
        val engine = PurgeEngine(context, media, saf, permissions, archive)
        assertEquals(PurgeEngine.DeletionEligibility.Permitted, engine.checkDeletionEligibility(emptyList()))
        for (mode in ExecutionMode.entries) {
            assertTrue(engine.preparePurge(emptyList(), mode) is PurgeEngine.PurgePlan.NoConfirmationNeeded)
        }
        verifyNoInteractions(context, media, saf, permissions, archive)
    }
}
