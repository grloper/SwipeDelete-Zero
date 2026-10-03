package com.swipedelete.zero

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Creates committed MediaStore rows as a real app would; the shell scanner
 * can leave _size null on API 35 and would never exercise the review screen. */
@RunWith(AndroidJUnit4::class)
class MediaFixtureInstrumentedTest {
    @Test fun createCommittedScreenshots() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val colors = intArrayOf(Color.rgb(80, 170, 230), Color.rgb(170, 90, 220), Color.rgb(30, 210, 180))
        // The normal CI run stays tiny. An isolated device can opt into the large-library journey.
        val count = InstrumentationRegistry.getArguments().getString("fixtureCount")
            ?.toIntOrNull()?.coerceIn(3, 1000) ?: 3
        repeat(count) { index ->
            val color = colors[index % colors.size]
            val name = "Screenshot_fixture_$index.png"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(collection, values))
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            try {
                resolver.openOutputStream(uri, "w")!!.use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            } finally {
                bitmap.recycle()
            }
            val commit = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            assertEquals(1, resolver.update(uri, commit, null, null))
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.IS_PENDING), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getLong(0) > 0)
                assertEquals(0, it.getInt(1))
            }
        }
        println("Committed $count synthetic screenshot rows with positive sizes and IS_PENDING=0")
    }
}
