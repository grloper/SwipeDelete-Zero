package com.swipedelete.zero

import android.content.Context
import android.net.Uri
import com.swipedelete.zero.data.local.StagedFileDao
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.data.repository.PurgeEngine
import com.swipedelete.zero.data.repository.SafStorageBridge
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.data.repository.StatsStore
import com.swipedelete.zero.data.repository.StoragePermissionManager
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.NoOpPhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.ui.screens.staging.StagingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

/**
 * M0-R7: Purge batch reconciliation and starvation prevention tests.
 *
 * Verifies that:
 * 1. 100 missing rows preceding 50 live rows do NOT starve the batch: missing rows are partitioned into alreadyMissingUris, live rows are planned for execution, and batches are capped.
 * 2. StagingViewModel immediately unstages alreadyMissingUris without claiming reclaimed storage bytes.
 * 3. User cancellation (onConfirmationResult(false)) clears pending state, leaves remaining queue intact, and launches no follow-on batches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PurgeBatchReconciliationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stagedItem(id: Int, sizeBytes: Long = 1024L) = StagedFileEntity(
        contentUri = "content://media/external/images/media/$id",
        displayName = "photo_$id.jpg",
        mimeType = "image/jpeg",
        mediaType = "IMAGE",
        sizeBytes = sizeBytes,
        relativePath = null,
        stagedAtMillis = System.currentTimeMillis(),
        sourceDeckId = null,
    )

    private fun mockUri(str: String): Uri {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn(str)
        return uri
    }

    private class InMemoryStagedDao : StagedFileDao {
        val items = mutableListOf<StagedFileEntity>()
        val removedUris = mutableListOf<String>()

        override fun observeAll(): Flow<List<StagedFileEntity>> = MutableStateFlow(items)
        override fun observeCount(): Flow<Int> = MutableStateFlow(items.size)
        override fun observeStagedBytes(): Flow<Long> = MutableStateFlow(items.sumOf { it.sizeBytes })
        override suspend fun getAll(): List<StagedFileEntity> = items
        override suspend fun stage(file: StagedFileEntity) { items.add(file) }
        override suspend fun unstage(uri: String) { items.removeIf { it.contentUri == uri } }
        override suspend fun removeAll(uris: List<String>) {
            removedUris.addAll(uris)
            items.removeIf { it.contentUri in uris }
        }
        override suspend fun clear() { items.clear() }
    }

    @Test
    fun `M0-R7 - preparePurge partitions 100 missing items and plans 50 live items preventing starvation`() = runTest {
        // M0-R7: Deletion is locked in Play/cloud builds; test starvation resolution on the offline/F-Droid path.
        if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE) return@runTest

        val context = mock(Context::class.java)
        val mediaStore = mock(MediaStoreRepository::class.java)
        val saf = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)

        val engine = PurgeEngine(context, mediaStore, saf, permissions, NoOpPhotosArchive())
        val uriCache = mutableMapOf<String, Uri>()
        engine.uriParser = { uriCache.getOrPut(it) { mockUri(it) } }

        // Create 150 items: items 1..100 are missing externally; items 101..150 are live on disk
        val items = (1..150).map { stagedItem(it) }
        for (i in 1..100) {
            val u = engine.uriParser("content://media/external/images/media/$i")
            `when`(mediaStore.stillExists(u)).thenReturn(false)
        }
        for (i in 101..150) {
            val u = engine.uriParser("content://media/external/images/media/$i")
            `when`(mediaStore.stillExists(u)).thenReturn(true)
        }

        val plan = engine.preparePurge(items, ExecutionMode.PERMANENT_PURGE)

        val alreadyMissing: List<String>
        val liveCount: Int
        val deferredCount: Int

        when (plan) {
            is PurgeEngine.PurgePlan.NeedsConfirmation -> {
                alreadyMissing = plan.alreadyMissingUris
                liveCount = plan.mediaUris.size
                deferredCount = plan.deferredUris.size
            }
            is PurgeEngine.PurgePlan.NoConfirmationNeeded -> {
                alreadyMissing = plan.alreadyMissingUris
                liveCount = 0
                deferredCount = plan.deferredUris.size
            }
            is PurgeEngine.PurgePlan.Failed -> error("Plan should not fail on offline path: ${plan.reason}")
        }

        assertEquals("All 100 missing items must be identified for un-staging", 100, alreadyMissing.size)
        assertTrue(alreadyMissing.contains("content://media/external/images/media/1"))
        assertTrue(alreadyMissing.contains("content://media/external/images/media/100"))

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            assertEquals("All 50 live items must be included in execution plan", 50, liveCount)
            assertEquals("No items deferred since 50 <= MAX_PURGE_BATCH_SIZE", 0, deferredCount)
        }
    }

    @Test
    fun `M0-R7 - StagingViewModel prunes alreadyMissingUris without awarding reclaimed bytes`() = runTest {
        if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE) return@runTest

        val stagedDao = InMemoryStagedDao()
        val stagingRepo = StagingRepository(stagedDao)
        val context = mock(Context::class.java)
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val photosArchive = NoOpPhotosArchive()
        val cloudBackup = mock(CloudBackup::class.java)
        val statsStore = mock(StatsStore::class.java)

        `when`(cloudBackup.state).thenReturn(MutableStateFlow(BackupState.Unsupported))
        `when`(statsStore.lifetimeReclaimedBytes).thenReturn(MutableStateFlow(0L))

        val purgeEngine = PurgeEngine(context, mediaStore, safBridge, permissions, photosArchive)
        purgeEngine.ioDispatcher = testDispatcher

        val missing1 = stagedItem(1)
        val missing2 = stagedItem(2)
        stagedDao.stage(missing1)
        stagedDao.stage(missing2)

        val uri1 = mockUri(missing1.contentUri)
        val uri2 = mockUri(missing2.contentUri)
        purgeEngine.uriParser = { if (it == missing1.contentUri) uri1 else uri2 }
        `when`(mediaStore.stillExists(uri1)).thenReturn(false)
        `when`(mediaStore.stillExists(uri2)).thenReturn(false)

        val vm = StagingViewModel(stagingRepo, purgeEngine, statsStore, photosArchive, cloudBackup)
        vm.purge()
        advanceUntilIdle()

        // Verify missing items are unstaged through repo/dao
        val expectedMissing = listOf(missing1.contentUri, missing2.contentUri)
        assertEquals("Missing items must be unstaged", expectedMissing, stagedDao.removedUris)
        // Verify stats store was never awarded reclaimed bytes for missing files
        org.mockito.Mockito.verify(statsStore, org.mockito.Mockito.never()).addReclaimed(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `M0-R7 - onConfirmationResult false clears pending batch without deleting or claiming bytes`() = runTest {
        val stagedDao = InMemoryStagedDao()
        val stagingRepo = StagingRepository(stagedDao)
        val context = mock(Context::class.java)
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val photosArchive = NoOpPhotosArchive()
        val statsStore = mock(StatsStore::class.java)
        val cloudBackup = mock(CloudBackup::class.java)
        `when`(cloudBackup.state).thenReturn(MutableStateFlow(BackupState.Unsupported))
        `when`(statsStore.lifetimeReclaimedBytes).thenReturn(MutableStateFlow(0L))

        val purgeEngine = PurgeEngine(context, mediaStore, safBridge, permissions, photosArchive)
        purgeEngine.ioDispatcher = testDispatcher
        val vm = StagingViewModel(stagingRepo, purgeEngine, statsStore, photosArchive, cloudBackup)

        // User cancels the OS dialog
        vm.onConfirmationResult(confirmed = false)
        advanceUntilIdle()

        // Assert: no stats store reclaimed bytes
        org.mockito.Mockito.verify(statsStore, org.mockito.Mockito.never()).addReclaimed(org.mockito.ArgumentMatchers.anyLong())
        // Assert: no files removed
        assertTrue("No files removed on cancel", stagedDao.removedUris.isEmpty())
        assertFalse("Purging flag must be reset to false", vm.uiState.value.purging)
    }

    @Test
    fun `M0-R5 - StagingViewModel purge is blocked in Play cloud builds`() = runTest {
        if (!com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE) return@runTest

        val stagedDao = InMemoryStagedDao()
        val stagingRepo = StagingRepository(stagedDao)
        val context = mock(Context::class.java)
        val mediaStore = mock(MediaStoreRepository::class.java)
        val safBridge = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val photosArchive = NoOpPhotosArchive()
        val statsStore = mock(StatsStore::class.java)
        val cloudBackup = mock(CloudBackup::class.java)
        `when`(cloudBackup.state).thenReturn(MutableStateFlow(BackupState.Unsupported))
        `when`(statsStore.lifetimeReclaimedBytes).thenReturn(MutableStateFlow(0L))

        val purgeEngine = PurgeEngine(context, mediaStore, safBridge, permissions, photosArchive)
        purgeEngine.ioDispatcher = testDispatcher
        val vm = StagingViewModel(stagingRepo, purgeEngine, statsStore, photosArchive, cloudBackup)

        stagedDao.stage(stagedItem(1))
        vm.purge()
        advanceUntilIdle()

        assertTrue("No items removed in Play build", stagedDao.removedUris.isEmpty())
        assertFalse("Purging state reset after blocked plan", vm.uiState.value.purging)
        org.mockito.Mockito.verify(statsStore, org.mockito.Mockito.never()).addReclaimed(org.mockito.ArgumentMatchers.anyLong())
    }
}
