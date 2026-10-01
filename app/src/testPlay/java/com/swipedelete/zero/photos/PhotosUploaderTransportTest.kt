package com.swipedelete.zero.photos

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.net.URL

/** Synthetic URLs, bearer strings and bytes only; no Google service is contacted. */
class PhotosUploaderTransportTest {
    @Test
    fun `unsafe session headers are rejected before being returned and connection is closed`() {
        for (destination in unsafeSessions) {
            val connection = PhotosFixtureConnection(UPLOADS, headers = mapOf("X-Goog-Upload-URL" to destination))
            val calls = mutableListOf<String>()
            val uploader = PhotosUploader().apply {
                connectionFactory = { calls.add(it); connection }
            }
            val error = assertThrows(PhotosUploader.UnexpectedDestinationException::class.java) {
                uploader.startSession(TOKEN, "image/jpeg", 4)
            }
            assertEquals(listOf(UPLOADS), calls)
            assertTrue(connection.closed)
            assertFalse("Do not expose a session capability", error.message!!.contains("secret-capability"))
        }
    }

    @Test
    fun `unsafe persisted sessions cannot open connections for queries or media chunks`() {
        val uploader = PhotosUploader().apply {
            connectionFactory = { fail("No connection may be created for an unsafe session"); error("unreachable") }
        }
        for (destination in unsafeSessions) {
            assertThrows(PhotosUploader.UnexpectedDestinationException::class.java) {
                uploader.querySession(TOKEN, destination)
            }
            assertThrows(PhotosUploader.UnexpectedDestinationException::class.java) {
                uploader.uploadChunk(TOKEN, destination, byteArrayOf(1, 2), 2, 0, true)
            }
        }
    }

    @Test
    fun `documented service sessions retain opaque query and support explicit default port`() {
        val sessions = listOf(
            SESSION,
            "https://photoslibrary.googleapis.com:443/v1/uploads?upload_id=synthetic%2Fid%2B&upload_protocol=resumable&opaque=1&opaque=2",
            "HTTPS://PHOTOSLIBRARY.GOOGLEAPIS.COM/v1/uploads?upload_id=synthetic",
        )
        for (sessionUrl in sessions) {
            val calls = mutableListOf<PhotosFixtureConnection>()
            val destinations = mutableListOf<String>()
            val uploader = PhotosUploader().apply {
                connectionFactory = { url ->
                    destinations.add(url)
                    PhotosFixtureConnection(url, headers = if (calls.isEmpty()) mapOf(
                        "X-Goog-Upload-URL" to sessionUrl,
                        "X-Goog-Upload-Chunk-Granularity" to "262144",
                    ) else mapOf("X-Goog-Upload-Status" to "active", "X-Goog-Upload-Size-Received" to "40"))
                        .also(calls::add)
                }
            }
            val session = uploader.startSession(TOKEN, "image/jpeg", 100)
            assertEquals(sessionUrl, session.uploadUrl)
            assertEquals(262144L, session.chunkGranularityBytes)
            val state = uploader.querySession(TOKEN, session.uploadUrl)
            assertEquals(40L, state.offset)
            assertTrue(state.isResumable)
            assertEquals(sessionUrl, destinations.last())
            assertEquals("query", calls.last().getRequestProperty("X-Goog-Upload-Command"))
            assertEquals("100", calls.first().getRequestProperty("X-Goog-Upload-Raw-Size"))
            assertEquals("resumable", calls.first().getRequestProperty("X-Goog-Upload-Protocol"))
            assertTrue(calls.all { it.closed && !it.instanceFollowRedirects })
        }
    }

    @Test
    fun `all redirect statuses fail without a second request or exposing Location`() {
        for (status in listOf(301, 302, 303, 307, 308)) {
            for (location in listOf(SESSION, "https://collector.example/secret-capability")) {
                for (operation in operations) {
                    var calls = 0
                    val connection = PhotosFixtureConnection(UPLOADS, status,
                        headers = mapOf("Location" to location), body = "secret-capability")
                    val uploader = PhotosUploader().apply { connectionFactory = { calls++; connection } }
                    val error = assertThrows(PhotosUploader.HttpStatusException::class.java) { operation(uploader) }
                    assertEquals(status, error.code)
                    assertTrue(error.message!!.contains("redirect rejected"))
                    assertFalse(error.message!!.contains("secret-capability"))
                    assertEquals(1, calls)
                    assertTrue(connection.closed)
                    assertFalse(connection.instanceFollowRedirects)
                }
            }
        }
    }

    @Test
    fun `real loopback transport never replays a redirect to a second host`() {
        // Map the already-validated service URL to loopback only at the injected
        // factory. Production validation is not relaxed and no service is called.
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val redirectedRequests = AtomicInteger()
        val initialRequests = AtomicInteger()
        val authorizationHeaders = CopyOnWriteArrayList<String?>()
        val serverFailure = AtomicReference<Throwable?>()
        val serverThread = Thread {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val requestLine = input.readLine() ?: error("Missing request line")
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = input.readLine() ?: error("Incomplete request headers")
                            if (line.isEmpty()) break
                            headers[line.substringBefore(":").lowercase()] = line.substringAfter(":").trim()
                        }
                        // Fixtures use only four ASCII-valued synthetic bytes.
                        repeat(headers["content-length"]?.toInt() ?: 0) {
                            check(input.read() >= 0) { "Incomplete synthetic request body" }
                        }
                        val redirected = requestLine.contains(" /collect ")
                        if (redirected) redirectedRequests.incrementAndGet() else {
                            initialRequests.incrementAndGet()
                            authorizationHeaders.add(headers["authorization"])
                        }
                        val response = if (redirected) "HTTP/1.1 200 OK\r\n" else
                            "HTTP/1.1 302 Found\r\nLocation: http://localhost:${server.localPort}/collect\r\n"
                        socket.getOutputStream().write(
                            (response + "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                        )
                    }
                }
            } catch (error: Throwable) {
                if (error !is SocketException || !server.isClosed) serverFailure.set(error)
            }
        }.apply { isDaemon = true; start() }
        try {
            val uploader = PhotosUploader().apply {
                connectionFactory = { URL("http://127.0.0.1:${server.localPort}/initial").openConnection() as HttpURLConnection }
            }
            for (operation in operations) {
                assertEquals(302, assertThrows(PhotosUploader.HttpStatusException::class.java) { operation(uploader) }.code)
            }
            assertEquals(operations.size, initialRequests.get())
            assertEquals(List(operations.size) { "Bearer $TOKEN" }, authorizationHeaders.toList())
            assertEquals("Redirect target must never receive a token or bytes", 0, redirectedRequests.get())
        } finally {
            server.close()
            serverThread.join(6_000)
        }
        assertFalse("Loopback server must terminate", serverThread.isAlive)
        assertNull("Loopback server must finish without hidden failures", serverFailure.get())
    }

    @Test
    fun `503 retry queries saved session and resumes at server confirmed offset`() {
        val calls = mutableListOf<PhotosFixtureConnection>()
        val uploader = PhotosUploader().apply {
            connectionFactory = { url ->
                val connection = when (calls.size) {
                    0 -> PhotosFixtureConnection(url, status = 503)
                    1 -> PhotosFixtureConnection(url, headers = mapOf(
                        "X-Goog-Upload-Status" to "active", "X-Goog-Upload-Size-Received" to "2"))
                    else -> PhotosFixtureConnection(url, body = " synthetic-finalize-token \n")
                }
                calls.add(connection)
                connection
            }
        }
        assertEquals(503, assertThrows(PhotosUploader.HttpStatusException::class.java) {
            uploader.uploadChunk(TOKEN, SESSION, byteArrayOf(1, 2, 3, 4), 4, 0, true)
        }.code)
        val result = uploader.querySession(TOKEN, SESSION)
        assertTrue(result.isResumable)
        assertEquals("synthetic-finalize-token",
            uploader.uploadChunk(TOKEN, SESSION, byteArrayOf(3, 4), 2, result.offset, true))
        assertEquals(listOf("0", null, "2"), calls.map { it.getRequestProperty("X-Goog-Upload-Offset") })
        assertEquals(listOf("upload, finalize", "query", "upload, finalize"),
            calls.map { it.getRequestProperty("X-Goog-Upload-Command") })
        assertArrayEquals(byteArrayOf(3, 4), calls.last().sent.toByteArray())
        assertTrue(calls.all { it.url.toString() == SESSION && it.closed && !it.instanceFollowRedirects })
    }

    @Test
    fun `nonfinal chunk still returns no token and empty finalize receipt fails closed`() {
        val calls = mutableListOf<PhotosFixtureConnection>()
        val uploader = PhotosUploader().apply {
            connectionFactory = { PhotosFixtureConnection(it, body = " \n").also(calls::add) }
        }
        assertNull(uploader.uploadChunk(TOKEN, SESSION, byteArrayOf(1), 1, 0, false))
        assertEquals("upload", calls.first().getRequestProperty("X-Goog-Upload-Command"))
        assertThrows(PhotosUploader.HttpStatusException::class.java) {
            uploader.uploadChunk(TOKEN, SESSION, byteArrayOf(2), 1, 1, true)
        }
        assertTrue(calls.all { it.closed })
    }

    @Test
    fun `missing session header and output or response failures always disconnect`() {
        val missing = PhotosFixtureConnection(UPLOADS)
        val uploader = PhotosUploader().apply { connectionFactory = { missing } }
        assertThrows(PhotosUploader.HttpStatusException::class.java) { uploader.startSession(TOKEN, "image/jpeg", 4) }
        assertTrue(missing.closed)
        for (operation in operations) {
            for (failure in listOf("output", "response")) {
                val connection = PhotosFixtureConnection(UPLOADS, failure = failure)
                uploader.connectionFactory = { connection }
                assertThrows(IOException::class.java) { operation(uploader) }
                assertTrue("$failure failure must disconnect", connection.closed)
            }
        }
        val readFailure = PhotosFixtureConnection(SESSION, failure = "input")
        uploader.connectionFactory = { readFailure }
        assertThrows(IOException::class.java) { uploader.uploadChunk(TOKEN, SESSION, byteArrayOf(1), 1, 0, true) }
        assertTrue(readFailure.closed)
    }

    companion object {
        internal const val TOKEN = "synthetic-bearer-token"
        internal const val UPLOADS = "https://photoslibrary.googleapis.com/v1/uploads"
        internal const val SESSION = "$UPLOADS?upload_id=synthetic-session&upload_protocol=resumable"
        private val operations: List<(PhotosUploader) -> Unit> = listOf(
            { it.startSession(TOKEN, "image/jpeg", 4); Unit },
            { it.querySession(TOKEN, SESSION); Unit },
            { it.uploadChunk(TOKEN, SESSION, byteArrayOf(1, 2, 3, 4), 4, 0, true); Unit },
        )
        private val unsafeSessions = listOf(
            "http://photoslibrary.googleapis.com/v1/uploads?secret-capability",
            "https://collector.example/v1/uploads?secret-capability",
            "https://photoslibrary.googleapis.com.collector.example/v1/uploads?secret-capability",
            "https://sub.photoslibrary.googleapis.com/v1/uploads?secret-capability",
            "https://www.google.com/v1/uploads?secret-capability",
            "https://upload.google.com/v1/uploads?secret-capability",
            "https://user@photoslibrary.googleapis.com/v1/uploads?secret-capability",
            "https://photoslibrary.googleapis.com@collector.example/v1/uploads?secret-capability",
            "https://photoslibrary.googleapis.com:80/v1/uploads?secret-capability",
            "https://photoslibrary.googleapis.com:444/v1/uploads?secret-capability",
            "https://photoslibrary.googleapis.com:/v1/uploads?secret-capability",
            "https://photoslibrary.googleapis.com./v1/uploads?secret-capability",
            "$UPLOADS?secret-capability#fragment",
            "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate?secret-capability",
            "https://photoslibrary.googleapis.com/v1/%75ploads?secret-capability",
            "https://photoslibrary.googleapis.com/v1/../v1/uploads?secret-capability",
            UPLOADS, "$UPLOADS?", "/v1/uploads?secret-capability",
            "//photoslibrary.googleapis.com/v1/uploads?secret-capability",
            " https://photoslibrary.googleapis.com/v1/uploads?secret-capability",
            "$UPLOADS?secret-capability\r\nX-Injected: yes",
            "https://127.0.0.1/v1/uploads?secret-capability",
            "https://[::1]/v1/uploads?secret-capability",
        )
    }
}

internal class PhotosFixtureConnection(
    url: String,
    private val status: Int = 200,
    private val headers: Map<String, String> = emptyMap(),
    private val body: String = "",
    private val failure: String? = null,
) : HttpURLConnection(URL(url)) {
    val sent = ByteArrayOutputStream()
    var closed = false
    override fun connect() {}
    override fun disconnect() { closed = true }
    override fun usingProxy() = false
    override fun getOutputStream(): OutputStream {
        assertFalse("Never auto-follow authenticated uploads", instanceFollowRedirects)
        if (failure == "output") throw IOException("synthetic output failure")
        return sent
    }
    override fun getInputStream(): InputStream {
        if (failure == "input") throw IOException("synthetic input failure")
        return body.byteInputStream()
    }
    override fun getErrorStream(): InputStream = body.byteInputStream()
    override fun getResponseCode(): Int {
        assertFalse("Never auto-follow authenticated requests", instanceFollowRedirects)
        assertEquals("Bearer ${PhotosUploaderTransportTest.TOKEN}", getRequestProperty("Authorization"))
        if (failure == "response") throw IOException("synthetic response failure")
        return status
    }
    override fun getHeaderField(name: String): String? = headers[name]
}
