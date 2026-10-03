package com.swipedelete.zero

import android.content.Context
import com.swipedelete.zero.data.repository.*
import com.swipedelete.zero.domain.backup.NoOpPhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class Api29SafetyTest {
    @Test fun `injected modern sdk cannot invoke unavailable all files API`() {
        val manager = StoragePermissionManager(RuntimeEnvironment.getApplication())
        manager.sdkInt = 35
        assertFalse(manager.hasAllFilesAccess())
    }
    @Test fun `default grouped request refuses Android 10 before touching new MediaStore API`() {
        val context = mock(Context::class.java)
        val engine = PurgeEngine(context, mock(MediaStoreRepository::class.java),
            mock(SafStorageBridge::class.java), StoragePermissionManager(RuntimeEnvironment.getApplication()), NoOpPhotosArchive())
        for (mode in ExecutionMode.entries) {
            try {
                engine.requestBuilder(emptyList(), mode)
                fail("Unsupported grouped request must be refused")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("Android 11"))
            }
        }
    }
}
