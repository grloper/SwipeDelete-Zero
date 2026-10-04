package com.swipedelete.zero

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.security.MessageDigest

/** Original generated photographs, test APK only. Requires a disposable synthetic emulator. */
internal fun seedStudioPhotographs() {
    check(Build.HARDWARE in setOf("ranchu", "goldfish"))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").use { fd -> FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    resolver.query(collection, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("Studio_fixture_%"), null)!!.use {
        if (it.count >= 12) return
        check(it.count == 0) { "Partial synthetic fixture set; use a fresh review emulator" }
    }
    repeat(12) { index ->
        val asset = if (index % 2 == 0) "coast.png" else "citrus.png"
        val source = instrumentation.context.assets.open("studio/$asset").use { it.readBytes() }
        val taken = System.currentTimeMillis() - (index / 4) * 32L * 24 * 60 * 60 * 1000 - index * 1000L
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Studio_fixture_${index}_$asset")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/StudioFixtures/")
            put(MediaStore.Images.ImageColumns.DATE_TAKEN, taken)
            put(MediaStore.MediaColumns.DATE_MODIFIED, taken / 1000)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(collection, values))
        resolver.openOutputStream(uri, "w")!!.use { it.write(source) }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
        val readBack = resolver.openInputStream(uri)!!.use { it.readBytes() }
        check(MessageDigest.getInstance("SHA-256").digest(source).contentEquals(MessageDigest.getInstance("SHA-256").digest(readBack)))
    }
}
