package com.swipedelete.zero

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.data.repository.StoragePermissionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaCollectionProjectionTest {
    @Test
    fun `collection projections return images videos and audio without incompatible columns`() = runBlocking {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        val seen = mutableListOf<String>()
        `when`(resolver.query(any(Uri::class.java), any(Array<String>::class.java), anyString(), isNull(), anyString()))
            .thenAnswer { call ->
                val uri = call.getArgument<Uri>(0).toString()
                val columns = call.getArgument<Array<String>>(1)
                seen.add(uri)
                if (uri.contains("images")) assertFalse(columns.contains(MediaStore.MediaColumns.DURATION))
                if (uri.contains("audio")) assertFalse(columns.contains(MediaStore.MediaColumns.WIDTH))
                if (uri.contains("video")) assertTrue(columns.contains(MediaStore.MediaColumns.DURATION))
                MatrixCursor(columns).apply {
                    addRow(columns.map { column -> when(column) {
                        MediaStore.MediaColumns._ID -> 1L
                        MediaStore.MediaColumns.DISPLAY_NAME -> "fixture.png"
                        MediaStore.MediaColumns.MIME_TYPE -> "image/png"
                        MediaStore.MediaColumns.SIZE -> 1024L
                        MediaStore.MediaColumns.DATE_ADDED -> 1000L
                        MediaStore.MediaColumns.RELATIVE_PATH -> "Pictures/"
                        else -> 0L
                    }}.toTypedArray())
                }
            }
        val repo = MediaStoreRepository(context, mock(StoragePermissionManager::class.java))
        assertEquals(2, repo.queryVisualMedia().size)
        assertEquals(1, repo.queryAudio().size)
        assertEquals(3, seen.size)
    }
}
