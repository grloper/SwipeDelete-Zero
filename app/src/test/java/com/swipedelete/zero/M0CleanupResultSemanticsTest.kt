package com.swipedelete.zero

import android.content.Context
import android.net.Uri
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.data.repository.MediaStoreRepository.MediaItemState
import com.swipedelete.zero.data.repository.PurgeEngine
import com.swipedelete.zero.data.repository.SafStorageBridge
import com.swipedelete.zero.data.repository.StoragePermissionManager
import com.swipedelete.zero.domain.backup.NoOpPhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Cleanup result semantics tests for M0:
 * DELETE-01: Per-item post-deletion reconciliation; unreadable != deleted; independently distinguish absent, present, trashed, unknown.
 * DELETE-02: Safe batch-size limits and first-batch cancellation with no implicit follow-on batches.
 */
class M0CleanupResultSemanticsTest {

    private fun stagedItem(id: Int) = StagedFileEntity(
        contentUri = "content://media/external/images/media/$id",
        displayName = "photo_$id.jpg",
        mimeType = "image/jpeg",
        mediaType = "IMAGE",
        sizeBytes = 2048L,
        relativePath = null,
        stagedAtMillis = System.currentTimeMillis(),
        sourceDeckId = null,
    )

    private fun mockUri(str: String): Uri {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn(str)
        return uri
    }

    // =========================================================================
    // DELETE-01: Post-OS Result Reconciliation (Unreadable != Deleted)
    // =========================================================================

    @Test
    fun `DELETE-01 - permanent purge confirms only ABSENT items, rejecting PRESENT, TRASHED, and UNKNOWN`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val saf = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val engine = PurgeEngine(mock(Context::class.java), mediaStore, saf, permissions, NoOpPhotosArchive())

        val uriAbsent = mockUri("content://media/external/images/media/1")
        val uriPresent = mockUri("content://media/external/images/media/2")
        val uriTrashed = mockUri("content://media/external/images/media/3")
        val uriUnknown = mockUri("content://media/external/images/media/4")

        `when`(mediaStore.inspectMediaState(uriAbsent)).thenReturn(MediaItemState.ABSENT)
        `when`(mediaStore.inspectMediaState(uriPresent)).thenReturn(MediaItemState.PRESENT)
        `when`(mediaStore.inspectMediaState(uriTrashed)).thenReturn(MediaItemState.TRASHED)
        `when`(mediaStore.inspectMediaState(uriUnknown)).thenReturn(MediaItemState.UNKNOWN)

        val batch = listOf(uriAbsent, uriPresent, uriTrashed, uriUnknown)
        val confirmed = engine.confirmMediaPurged(batch, ExecutionMode.PERMANENT_PURGE)

        // Only the confirmed absent item must be reported as purged!
        assertEquals("Only ABSENT URI must be confirmed", listOf(uriAbsent.toString()), confirmed)
        assertFalse("PRESENT file must never be confirmed purged", confirmed.contains(uriPresent.toString()))
        assertFalse("TRASHED file is not permanently deleted", confirmed.contains(uriTrashed.toString()))
        assertFalse("UNKNOWN (permission lost/query error) must NEVER be treated as deleted", confirmed.contains(uriUnknown.toString()))
    }

    @Test
    fun `DELETE-01 - OS Trash confirms only verified TRASHED items, rejecting UNKNOWN and PRESENT`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val saf = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val engine = PurgeEngine(mock(Context::class.java), mediaStore, saf, permissions, NoOpPhotosArchive())

        val uriTrashed1 = mockUri("content://media/external/images/media/10")
        val uriTrashed2 = mockUri("content://media/external/images/media/11")
        val uriPresent = mockUri("content://media/external/images/media/12")
        val uriUnknown = mockUri("content://media/external/images/media/13")

        `when`(mediaStore.inspectMediaState(uriTrashed1)).thenReturn(MediaItemState.TRASHED)
        `when`(mediaStore.inspectMediaState(uriTrashed2)).thenReturn(MediaItemState.TRASHED)
        `when`(mediaStore.inspectMediaState(uriPresent)).thenReturn(MediaItemState.PRESENT)
        `when`(mediaStore.inspectMediaState(uriUnknown)).thenReturn(MediaItemState.UNKNOWN)

        val batch = listOf(uriTrashed1, uriTrashed2, uriPresent, uriUnknown)
        val confirmed = engine.confirmMediaPurged(batch, ExecutionMode.OS_TRASH_30_DAY)

        assertEquals(
            "Only confirmed TRASHED items qualify",
            listOf(uriTrashed1.toString(), uriTrashed2.toString()),
            confirmed,
        )
        assertFalse("Active item must not be confirmed trashed", confirmed.contains(uriPresent.toString()))
        assertFalse("Unreadable item must not be assumed trashed", confirmed.contains(uriUnknown.toString()))
    }

    // =========================================================================
    // DELETE-02: Safe Batch Sizing and Cancellation Discipline
    // =========================================================================

    @Test
    fun `DELETE-02 - batch size is bounded to maximum purge batch limit and cancellation leaves remainder untouched`() = runTest {
        val mediaStore = mock(MediaStoreRepository::class.java)
        val saf = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val checkedUris = mutableListOf<String>()

        // Subclass engine with Permitted eligibility (modeling offline/post-gate execution) to verify batch bounding
        val engine = object : PurgeEngine(mock(Context::class.java), mediaStore, saf, permissions, NoOpPhotosArchive()) {
            override suspend fun checkDeletionEligibility(staged: List<StagedFileEntity>) = DeletionEligibility.Permitted
        }
        engine.uriParser = { uriStr ->
            val u = mockUri(uriStr)
            `when`(mediaStore.stillExists(u)).thenAnswer {
                checkedUris += uriStr
                false // return false so preparePurge returns NoConfirmationNeeded without needing Android R static MediaStore call
            }
            u
        }

        // Create 150 staged items
        val largeList = (1..150).map { stagedItem(it) }
        val plan = engine.preparePurge(largeList, ExecutionMode.PERMANENT_PURGE)

        // The batch must be strictly capped at MAX_PURGE_BATCH_SIZE (100)
        assertEquals(100, PurgeEngine.MAX_PURGE_BATCH_SIZE)
        assertEquals("Only the first 100 items may be evaluated in a single batch", 100, checkedUris.size)
        assertTrue(plan is PurgeEngine.PurgePlan.NoConfirmationNeeded)
        // Remainder items (101..150) were never touched or automatically dispatched
        assertFalse(checkedUris.contains("content://media/external/images/media/101"))
    }
}
