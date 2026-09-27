package com.swipedelete.zero

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class VerifiedDeletionGateTest {
    private fun item(id: String) = StagedFileEntity(
        contentUri = "content://media/external/images/media/$id",
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        mediaType = "IMAGE",
        sizeBytes = 100,
        relativePath = null,
        stagedAtMillis = 1,
        sourceDeckId = null,
    )

    @Test
    fun `an unverified item blocks the entire batch before any local mutation`() = runTest {
        val media = mock(MediaStoreRepository::class.java)
        val saf = mock(SafStorageBridge::class.java)
        val archive = fakeArchive { it.displayName != "missing.jpg" }
        val engine = PurgeEngine(
            mock(Context::class.java), media, saf,
            mock(StoragePermissionManager::class.java), archive,
        )
        engine.uriParser = { mock(Uri::class.java) }

        if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE) {
            val plan = engine.preparePurge(listOf(item("safe"), item("missing")), ExecutionMode.PERMANENT_PURGE)
            assertTrue(plan is PurgeEngine.PurgePlan.Failed)
            // Under M0-R5 immediate safety lock, verifyRemote is never touched
            assertTrue("Provider verifyRemote must not be called when locked", archive.checked.isEmpty())
            verifyNoInteractions(media, saf)
        }
    }

    @Test
    fun `offline or revoked remote proof blocks even Android Trash`() = runTest {
        val engine = PurgeEngine(
            mock(Context::class.java), mock(MediaStoreRepository::class.java),
            mock(SafStorageBridge::class.java), mock(StoragePermissionManager::class.java),
            fakeArchive { false },
        )
        engine.uriParser = { mock(Uri::class.java) }
        if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE) {
            assertTrue(engine.preparePurge(listOf(item("one")), ExecutionMode.OS_TRASH_30_DAY)
                is PurgeEngine.PurgePlan.Failed)
        }
    }

    private fun fakeArchive(check: (StagedFileEntity) -> Boolean) = object : PhotosArchive {
        val checked = mutableListOf<String>()
        override val isAvailable = true
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
}
