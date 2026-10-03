package com.swipedelete.zero

import androidx.room.Room
import com.swipedelete.zero.data.local.*
import com.swipedelete.zero.data.repository.ExclusionRepository
import com.swipedelete.zero.data.repository.ReviewSound
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExclusionUndoTest {
    @Test fun `unstar removes only selected file and repeated undo is harmless`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.exclusionDao()
            dao.add(ExclusionEntity(type = ExclusionEntity.TYPE_STARRED_FILE, uri = "content://media/images/1",
                perceptualHash = 12, folderPath = null, label = "one", createdAtMillis = 0))
            dao.add(ExclusionEntity(type = ExclusionEntity.TYPE_STARRED_FILE, uri = "content://media/images/2",
                perceptualHash = 22, folderPath = null, label = "two", createdAtMillis = 0))
            dao.add(ExclusionEntity(type = ExclusionEntity.TYPE_EXCLUDED_FOLDER, uri = null,
                perceptualHash = null, folderPath = "Pictures/Other", label = "folder", createdAtMillis = 0))
            val repository = ExclusionRepository(dao)
            repository.unstarItem("content://media/images/1")
            repository.unstarItem("content://media/images/1")
            val remaining = dao.observeAll().first()
            assertEquals(2, remaining.size)
            assertTrue(remaining.any { it.uri == "content://media/images/2" })
            assertTrue(remaining.any { it.folderPath == "Pictures/Other" })
            assertEquals(listOf(22L), dao.excludedHashes())
        } finally { db.close() }
    }
    @Test fun `sound mute persists across player recreation`() {
        val context = RuntimeEnvironment.getApplication()
        val sound = ReviewSound(context)
        sound.setEnabled(false)
        assertFalse(ReviewSound(context).enabled.value)
        sound.setEnabled(true)
        assertTrue(ReviewSound(context).enabled.value)
        sound.setEnabled(false)
    }
}
