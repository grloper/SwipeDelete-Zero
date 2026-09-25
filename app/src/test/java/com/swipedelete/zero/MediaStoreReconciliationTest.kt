package com.swipedelete.zero

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.data.repository.MediaStoreRepository.MediaItemState
import com.swipedelete.zero.data.repository.StoragePermissionManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * M0-R6: MediaStore reconciliation tests for access visibility and Android 14+ selected-media model.
 *
 * Verifies that:
 * 1. Permission denial fails closed to UNKNOWN, never ABSENT.
 * 2. Android 14+ limited visual access (READ_MEDIA_VISUAL_USER_SELECTED) fails closed to UNKNOWN on empty cursor.
 * 3. Full media access confirms ABSENT on empty cursor.
 * 4. Trashed rows confirm TRASHED.
 * 5. Present rows confirm PRESENT.
 * 6. Null cursor or query exceptions fail closed to UNKNOWN.
 */
class MediaStoreReconciliationTest {

    private fun mockUri(): Uri {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/images/media/42")
        return uri
    }

    @Test
    fun `M0-R6 - permission denial returns UNKNOWN, never ABSENT`() {
        val context = mock(Context::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        `when`(permissions.hasMediaAccess()).thenReturn(false)

        val repo = MediaStoreRepository(context, permissions)
        repo.sdkInt = 30
        val state = repo.inspectMediaState(mockUri())

        assertEquals("When permission is denied, state must fail closed to UNKNOWN", MediaItemState.UNKNOWN, state)
    }

    @Test
    fun `M0-R6 - Android 14 limited media access fails closed to UNKNOWN on empty cursor`() {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val cursor = mock(Cursor::class.java)

        `when`(context.contentResolver).thenReturn(resolver)
        `when`(permissions.hasMediaAccess()).thenReturn(true)
        `when`(permissions.hasLimitedMediaAccessOnly()).thenReturn(true) // Partial access only!
        `when`(resolver.query(any(), any(), any(), any())).thenReturn(cursor)
        `when`(cursor.moveToFirst()).thenReturn(false) // Not visible in current selection

        val repo = MediaStoreRepository(context, permissions)
        repo.sdkInt = 30
        val state = repo.inspectMediaState(mockUri())

        assertEquals(
            "Empty cursor under limited visual access cannot prove absence; must fail closed to UNKNOWN",
            MediaItemState.UNKNOWN,
            state,
        )
    }

    @Test
    fun `M0-R6 - full media access confirms ABSENT on empty cursor`() {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val cursor = mock(Cursor::class.java)

        `when`(context.contentResolver).thenReturn(resolver)
        `when`(permissions.hasMediaAccess()).thenReturn(true)
        `when`(permissions.hasLimitedMediaAccessOnly()).thenReturn(false) // Full media access!
        `when`(resolver.query(any(), any(), any(), any())).thenReturn(cursor)
        `when`(cursor.moveToFirst()).thenReturn(false)

        val repo = MediaStoreRepository(context, permissions)
        repo.sdkInt = 30
        val state = repo.inspectMediaState(mockUri())

        assertEquals("Empty cursor with full media access confirms ABSENT", MediaItemState.ABSENT, state)
    }

    @Test
    fun `M0-R6 - trashed row confirms TRASHED`() {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val cursor = mock(Cursor::class.java)

        `when`(context.contentResolver).thenReturn(resolver)
        `when`(permissions.hasMediaAccess()).thenReturn(true)
        `when`(permissions.hasLimitedMediaAccessOnly()).thenReturn(false)
        `when`(resolver.query(any(), any(), any(), any())).thenReturn(cursor)
        `when`(cursor.moveToFirst()).thenReturn(true)
        `when`(cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)).thenReturn(1)
        `when`(cursor.getInt(1)).thenReturn(1)

        val repo = MediaStoreRepository(context, permissions)
        repo.sdkInt = 30
        val state = repo.inspectMediaState(mockUri())

        assertEquals(MediaItemState.TRASHED, state)
    }

    @Test
    fun `M0-R6 - active untrashed row confirms PRESENT`() {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        val permissions = mock(StoragePermissionManager::class.java)
        val cursor = mock(Cursor::class.java)

        `when`(context.contentResolver).thenReturn(resolver)
        `when`(permissions.hasMediaAccess()).thenReturn(true)
        `when`(permissions.hasLimitedMediaAccessOnly()).thenReturn(false)
        `when`(resolver.query(any(), any(), any(), any())).thenReturn(cursor)
        `when`(cursor.moveToFirst()).thenReturn(true)
        `when`(cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)).thenReturn(1)
        `when`(cursor.getInt(1)).thenReturn(0)

        val repo = MediaStoreRepository(context, permissions)
        repo.sdkInt = 30
        val state = repo.inspectMediaState(mockUri())

        assertEquals(MediaItemState.PRESENT, state)
    }

    @Test
    fun `M0-R6 - null cursor or exception returns UNKNOWN`() {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        val permissions = mock(StoragePermissionManager::class.java)

        `when`(context.contentResolver).thenReturn(resolver)
        `when`(permissions.hasMediaAccess()).thenReturn(true)
        `when`(resolver.query(any(), any(), any(), any())).thenReturn(null)

        val repo = MediaStoreRepository(context, permissions)
        repo.sdkInt = 30
        assertEquals(MediaItemState.UNKNOWN, repo.inspectMediaState(mockUri()))

        `when`(resolver.query(any(), any(), any(), any())).thenThrow(SecurityException("Permission revoked"))
        assertEquals(MediaItemState.UNKNOWN, repo.inspectMediaState(mockUri()))
    }
}
