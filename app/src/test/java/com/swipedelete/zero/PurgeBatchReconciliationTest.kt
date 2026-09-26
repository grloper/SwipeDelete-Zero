package com.swipedelete.zero

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.swipedelete.zero.data.local.StagedFileDao
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.data.repository.MediaStoreRepository.MediaItemState
import com.swipedelete.zero.data.repository.PurgeEngine
import com.swipedelete.zero.data.repository.SafStorageBridge
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.data.repository.StatsStore
import com.swipedelete.zero.data.repository.StoragePermissionManager
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.NoOpPhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.ui.screens.staging.PurgeEffect
import com.swipedelete.zero.ui.screens.staging.StagingUiState
import com.swipedelete.zero.ui.screens.staging.StagingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * M0-R7 & M0-V2-01 & M0-V2-04: Purge batch reconciliation, four-state visibility, and cancellation tests.
 *
 * Verifies that:
 * 1. 100 missing rows preceding 50 live rows do NOT starve the batch: missing rows are partitioned into alreadyMissingUris, live rows are planned for execution, and batches are capped.
 * 2. StagingViewModel immediately unstages confirmed ABSENT without claiming reclaimed storage bytes.
 * 3. Four-state partition (PRESENT, ABSENT, TRASHED, UNKNOWN): only confirmed ABSENT/TRASHED are unstaged without byte credit; UNKNOWN MUST REMAIN STAGED.
 * 4. User cancellation (onConfirmationResult(false)) on an actual non-empty batch clears pending state, leaves the queue fully intact, awards zero bytes, and launches no follow-on batches.
 * 5. Flavor-specific tests use explicit JUnit assumptions (Assume.assumeFalse/assumeTrue) to report accurate skips rather than silent early returns.
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

        private val _flow = MutableStateFlow<List<StagedFileEntity>>(emptyList())

        private fun updateFlow() {
            _flow.value = items.toList()
        }

        override fun observeAll(): Flow<List<StagedFileEntity>> = _flow
        override fun observeCount(): Flow<Int> = MutableStateFlow(items.size)
        override fun observeStagedBytes(): Flow<Long> = MutableStateFlow(items.sumOf { it.sizeBytes })
        override suspend fun getAll(): List<StagedFileEntity> = items.toList()
        override suspend fun stage(file: StagedFileEntity) {
            items.add(file)
            updateFlow()
        }
        override suspend fun unstage(uri: String) {
            items.removeIf { it.contentUri == uri }
            updateFlow()
        }
        override suspend fun removeAll(uris: List<String>) {
            removedUris.addAll(uris)
            items.removeIf { it.contentUri in uris }
            updateFlow()
        }
        override suspend fun clear() {
            items.clear()
            updateFlow()
        }
    }

    @Test
    fun `M0-R7 - preparePurge partitions 100 missing items and plans 50 live items preventing starvation`() = runTest {
        assumeFalse("Deletion is locked in Play/cloud builds; test starvation on offline path", BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)

        val context = mock(Context::class.java)
        val mediaStore = mock(MediaStoreRepository::class.java)
        val saf = mock(SafStorageBridge::class.java)
        val permissions = mock(StoragePermissionManager::class.java)

        val engine = PurgeEngine(context, mediaStore, saf, permissions, NoOpPhotosArchive())
        engine.sdkInt = android.os.Build.VERSION_CODES.R
        engine.requestBuilder = { _, _ -> mock(IntentSender::class.java) }
        val uriCache = mutableMapOf<String, Uri>()
        engine.uriParser = { uriCache.getOrPut(it) { mockUri(it) } }

        // Create 150 items: items 1..100 are missing externally; items 101..150 are live on disk
        val items = (1..150).map { stagedItem(it) }
        for (i in 1..100) {
            val u = engine.uriParser("content://media/external/images/media/$i")
            `when`(mediaStore.inspectMediaState(u)).thenReturn(MediaItemState.ABSENT)
        }
        for (i in 101..150) {
            val u = engine.uriParser("content://media/external/images/media/$i")
            `when`(mediaStore.inspectMediaState(u)).thenReturn(MediaItemState.PRESENT)
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
        assertEquals("All 50 live items must be included in execution plan", 50, liveCount)
        assertEquals("No items deferred since 50 <= MAX_PURGE_BATCH_SIZE", 0, deferredCount)
    }

    @Test
    fun `M0-R7 - StagingViewModel prunes alreadyMissingUris without awarding reclaimed bytes`() = runTest {
        assumeFalse("Deletion is locked in Play/cloud builds; test on offline path", BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)

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
        `when`(mediaStore.inspectMediaState(uri1)).thenReturn(MediaItemState.ABSENT)
        `when`(mediaStore.inspectMediaState(uri2)).thenReturn(MediaItemState.ABSENT)

        val vm = StagingViewModel(stagingRepo, purgeEngine, statsStore, photosArchive, cloudBackup)
        vm.purge()
        advanceUntilIdle()

        // Verify missing items are unstaged through repo/dao
        val expectedMissing = listOf(missing1.contentUri, missing2.contentUri)
        assertEquals("Missing items must be unstaged", expectedMissing, stagedDao.removedUris)
        // Verify stats store was never awarded reclaimed bytes for missing files
        verify(statsStore, never()).addReclaimed(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `M0-V2-01 - 4-state resolution preserves UNKNOWN and reconciles only confirmed ABSENT and TRASHED`() = runTest {
        assumeFalse("Deletion is locked in Play/cloud builds; test on offline path", BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)

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
        purgeEngine.sdkInt = android.os.Build.VERSION_CODES.R
        purgeEngine.requestBuilder = { _, _ -> mock(IntentSender::class.java) }

        val presentItem = stagedItem(10)
        val absentItem = stagedItem(20)
        val trashedItem = stagedItem(30)
        val unknownItem = stagedItem(40) // E.g. restricted collection / permission failure

        stagedDao.stage(presentItem)
        stagedDao.stage(absentItem)
        stagedDao.stage(trashedItem)
        stagedDao.stage(unknownItem)

        val uriMap = mapOf(
            presentItem.contentUri to mockUri(presentItem.contentUri),
            absentItem.contentUri to mockUri(absentItem.contentUri),
            trashedItem.contentUri to mockUri(trashedItem.contentUri),
            unknownItem.contentUri to mockUri(unknownItem.contentUri),
        )
        purgeEngine.uriParser = { uriMap[it] ?: mockUri(it) }

        `when`(mediaStore.inspectMediaState(uriMap[presentItem.contentUri]!!)).thenReturn(MediaItemState.PRESENT)
        `when`(mediaStore.inspectMediaState(uriMap[absentItem.contentUri]!!)).thenReturn(MediaItemState.ABSENT)
        `when`(mediaStore.inspectMediaState(uriMap[trashedItem.contentUri]!!)).thenReturn(MediaItemState.TRASHED)
        `when`(mediaStore.inspectMediaState(uriMap[unknownItem.contentUri]!!)).thenReturn(MediaItemState.UNKNOWN)

        val vm = StagingViewModel(stagingRepo, purgeEngine, statsStore, photosArchive, cloudBackup)
        val collectedEffects = mutableListOf<PurgeEffect>()
        val effectJob = launch { vm.effect.collect { collectedEffects.add(it) } }

        vm.purge()
        advanceUntilIdle()

        // 1. ABSENT and TRASHED items are unstaged without claiming reclaimed bytes
        assertTrue("Absent item must be unstaged", stagedDao.removedUris.contains(absentItem.contentUri))
        assertTrue("Already-trashed item must be unstaged in trash mode", stagedDao.removedUris.contains(trashedItem.contentUri))

        // 2. UNKNOWN item MUST REMAIN STAGED!
        assertFalse("UNKNOWN item must NOT be removed from queue", stagedDao.removedUris.contains(unknownItem.contentUri))
        assertTrue("UNKNOWN item remains in staging queue", stagedDao.items.any { it.contentUri == unknownItem.contentUri })

        // 3. Zero bytes credited for externally absent or already-trashed items
        verify(statsStore, never()).addReclaimed(org.mockito.ArgumentMatchers.anyLong())

        // 4. Message emitted for blocked items
        assertTrue("Blocked message must be surfaced", collectedEffects.any { it is PurgeEffect.Message && it.text.contains("could not be verified") })

        effectJob.cancel()
    }

    @Test
    fun `M0-V2-04 - cancellation of actual non-empty batch preserves queue, clears pending, and launches no follow-on batches`() = runTest {
        assumeFalse("Deletion is locked in Play/cloud builds; test cancellation on offline path", BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)

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

        // Stage 5 real items
        val stagedItems = (1..5).map { stagedItem(it) }
        stagedItems.forEach { stagedDao.stage(it) }

        val purgeEngine = PurgeEngine(context, mediaStore, safBridge, permissions, photosArchive)
        purgeEngine.ioDispatcher = testDispatcher
        purgeEngine.sdkInt = android.os.Build.VERSION_CODES.R
        var confirmationRequestCount = 0
        purgeEngine.requestBuilder = { _, _ ->
            confirmationRequestCount++
            mock(IntentSender::class.java)
        }
        val uriMap = stagedItems.associate { it.contentUri to mockUri(it.contentUri) }
        purgeEngine.uriParser = { uriMap[it] ?: mockUri(it) }

        stagedItems.forEach { item ->
            `when`(mediaStore.inspectMediaState(uriMap[item.contentUri]!!)).thenReturn(MediaItemState.PRESENT)
        }

        val vm = StagingViewModel(stagingRepo, purgeEngine, statsStore, photosArchive, cloudBackup)

        // Actively collect WhileSubscribed uiState and effect flow
        val collectedStates = mutableListOf<StagingUiState>()
        val collectedEffects = mutableListOf<PurgeEffect>()
        val stateJob = launch { vm.uiState.collect { collectedStates.add(it) } }
        val effectJob = launch { vm.effect.collect { collectedEffects.add(it) } }

        // Start purge on non-empty queue
        vm.purge()
        advanceUntilIdle()

        // Verify: OS confirmation effect was emitted and purging flag was set
        assertTrue("Confirmation dialog must be requested for live batch", collectedEffects.any { it is PurgeEffect.LaunchConfirmation })
        assertEquals("Exactly 1 confirmation request must be created for live batch", 1, confirmationRequestCount)
        assertTrue("ViewModel should be in purging state while dialog is open", collectedStates.last().purging)

        // User dismisses / cancels OS confirmation dialog
        vm.onConfirmationResult(confirmed = false)
        advanceUntilIdle()

        // Assert 1: Zero files removed from staging queue
        assertTrue("No files removed on cancel", stagedDao.removedUris.isEmpty())
        assertEquals("Queue remains fully intact with all 5 items", 5, stagedDao.items.size)

        // Assert 2: Purging state reset to false
        assertFalse("Purging state must be false after cancellation", collectedStates.last().purging)

        // Assert 3: No bytes credited to stats store
        verify(statsStore, never()).addReclaimed(org.mockito.ArgumentMatchers.anyLong())

        // Assert 4: Confirmation request counter proves no second confirmation/batch begins after cancellation
        assertEquals("No second confirmation request or follow-on batch launched after cancellation", 1, confirmationRequestCount)

        stateJob.cancel()
        effectJob.cancel()
    }

    @Test
    fun `M0-R5 - StagingViewModel purge is blocked in Play cloud builds`() = runTest {
        assumeTrue("Deletion lock only applies to Play/cloud builds", BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)

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
        verify(statsStore, never()).addReclaimed(org.mockito.ArgumentMatchers.anyLong())
    }
}
