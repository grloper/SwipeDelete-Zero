package com.swipedelete.zero.photos

import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotosUploaderJsonTransportTest {
    @Test
    fun `batchCreate and media readback retain successful response handling`() {
        val calls = mutableListOf<PhotosFixtureConnection>()
        val uploader = PhotosUploader().apply {
            connectionFactory = { url ->
                PhotosFixtureConnection(url, body = if (url.contains("batchCreate")) {
                    """{"newMediaItemResults":[{"status":{"code":0},"mediaItem":{"id":"synthetic-media"}}]}"""
                } else {
                    """{"id":"synthetic-media","filename":"synthetic.jpg","mimeType":"image/jpeg","baseUrl":"https://photos.example/synthetic","productUrl":"https://photos.google.com/synthetic"}"""
                }).also(calls::add)
            }
        }
        assertEquals("synthetic-media", uploader.batchCreate(TOKEN, "synthetic-upload-token", "synthetic.jpg"))
        assertEquals("synthetic-media", uploader.getMediaItem(TOKEN, "synthetic-media").id)
        assertTrue(calls.first().sent.toString().contains("synthetic-upload-token"))
        assertTrue(calls.first().sent.toString().contains("synthetic.jpg"))
        assertTrue(calls.all { it.closed && !it.instanceFollowRedirects })
    }

    @Test
    fun `batchCreate and readback redirects cannot replay credentials or JSON`() {
        for (status in listOf(301, 302, 303, 307, 308)) {
            for (operation in operations) {
                var requests = 0
                val connection = PhotosFixtureConnection(PhotosUploaderTransportTest.UPLOADS, status,
                    headers = mapOf("Location" to "https://collector.example/collect"))
                val uploader = PhotosUploader().apply { connectionFactory = { requests++; connection } }
                assertEquals(status, assertThrows(PhotosUploader.HttpStatusException::class.java) { operation(uploader) }.code)
                assertEquals(1, requests)
                assertTrue(connection.closed)
                assertFalse(connection.instanceFollowRedirects)
            }
        }
    }

    @Test
    fun `batchCreate and readback disconnect after malformed JSON and IO failures`() {
        for (operation in operations) {
            val malformed = PhotosFixtureConnection(PhotosUploaderTransportTest.UPLOADS, body = "{")
            val uploader = PhotosUploader().apply { connectionFactory = { malformed } }
            assertThrows(JSONException::class.java) { operation(uploader) }
            assertTrue(malformed.closed)
            for (failure in listOf("input", "response")) {
                val connection = PhotosFixtureConnection(PhotosUploaderTransportTest.UPLOADS, failure = failure)
                uploader.connectionFactory = { connection }
                assertThrows(IOException::class.java) { operation(uploader) }
                assertTrue(connection.closed)
            }
        }
        val connection = PhotosFixtureConnection(PhotosUploaderTransportTest.UPLOADS, failure = "output")
        val uploader = PhotosUploader().apply { connectionFactory = { connection } }
        assertThrows(IOException::class.java) { uploader.batchCreate(TOKEN, "synthetic-upload-token", "synthetic.jpg") }
        assertTrue(connection.closed)
    }

    companion object {
        private const val TOKEN = PhotosUploaderTransportTest.TOKEN
        private val operations: List<(PhotosUploader) -> Unit> = listOf(
            { it.batchCreate(TOKEN, "synthetic-upload-token", "synthetic.jpg"); Unit },
            { it.getMediaItem(TOKEN, "synthetic-media"); Unit },
        )
    }
}
