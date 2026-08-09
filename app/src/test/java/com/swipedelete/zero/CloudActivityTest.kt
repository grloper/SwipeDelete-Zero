package com.swipedelete.zero

import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.domain.backup.CloudActivityMerge
import com.swipedelete.zero.domain.backup.CloudActivityStatus
import com.swipedelete.zero.domain.backup.CloudDestination
import com.swipedelete.zero.domain.backup.RemoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monitor's whole point is that it does not overstate what is known, so
 * these tests are mostly about which *claim* each row ends up making.
 */
class CloudActivityTest {

    private fun ledgerRow(
        uri: String,
        destination: CloudDestination = CloudDestination.PHOTOS,
        state: RemoteState = RemoteState.RECORDED,
        size: Long = 1_000,
        uploadedAt: Long = 100,
        verifiedAt: Long = 0,
    ) = BackedUpFileEntity(
        contentUri = uri,
        sizeBytes = size,
        remoteId = "remote-$uri",
        uploadedAtMillis = uploadedAt,
        destination = destination.name,
        displayName = "name-$uri",
        remoteState = state.name,
        verifiedAtMillis = verifiedAt,
    )

    private fun uploadRow(
        uri: String,
        state: String,
        size: Long = 1_000,
        sent: Long = 0,
        error: String? = null,
    ) = CloudUploadEntity(
        contentUri = uri,
        displayName = "name-$uri",
        mimeType = "image/jpeg",
        sizeBytes = size,
        state = state,
        bytesUploaded = sent,
        lastError = error,
        enqueuedAtMillis = 10,
        updatedAtMillis = 20,
    )

    @Test
    fun `remote state maps to distinct claims`() {
        val rows = CloudActivityMerge.merge(
            ledger = listOf(
                ledgerRow("a", state = RemoteState.RECORDED),
                ledgerRow("b", state = RemoteState.CONFIRMED),
                ledgerRow("c", state = RemoteState.MISSING),
                ledgerRow("d", state = RemoteState.UNKNOWN),
            ),
            uploads = emptyList(),
        ).associateBy { it.contentUri }

        assertEquals(CloudActivityStatus.Uploaded, rows.getValue("a").status)
        assertEquals(CloudActivityStatus.Verified, rows.getValue("b").status)
        assertEquals(CloudActivityStatus.Missing, rows.getValue("c").status)
        assertEquals(CloudActivityStatus.Unverified, rows.getValue("d").status)
    }

    /** An acknowledged write must never be presented as a verified copy. */
    @Test
    fun `uploaded and verified are different labels`() {
        assertTrue(CloudActivityStatus.Uploaded.label != CloudActivityStatus.Verified.label)
        assertTrue(CloudActivityStatus.Uploaded.label.contains("not re-checked"))
    }

    @Test
    fun `ledger row wins over a lingering queue row for the same file`() {
        val rows = CloudActivityMerge.merge(
            ledger = listOf(ledgerRow("a", state = RemoteState.CONFIRMED, verifiedAt = 500)),
            uploads = listOf(uploadRow("a", CloudUploadEntity.STATE_VERIFIED)),
        )
        assertEquals(1, rows.size)
        assertEquals(CloudActivityStatus.Verified, rows.first().status)
        assertEquals(500, rows.first().atMillis)
    }

    @Test
    fun `in-flight work sorts above finished work`() {
        val rows = CloudActivityMerge.merge(
            ledger = listOf(ledgerRow("done", uploadedAt = 9_000)),
            uploads = listOf(uploadRow("moving", CloudUploadEntity.STATE_UPLOADING, sent = 250)),
        )
        assertEquals("moving", rows.first().contentUri)
        assertEquals(0.25f, rows.first().progress!!, 0.0001f)
    }

    @Test
    fun `problems sort above ordinary finished rows`() {
        val rows = CloudActivityMerge.merge(
            ledger = listOf(
                ledgerRow("ok", state = RemoteState.CONFIRMED, uploadedAt = 9_000),
                ledgerRow("gone", state = RemoteState.MISSING, uploadedAt = 1),
            ),
            uploads = emptyList(),
        )
        assertEquals("gone", rows.first().contentUri)
    }

    @Test
    fun `a failed upload offers a retry`() {
        val row = CloudActivityMerge.merge(
            ledger = emptyList(),
            uploads = listOf(uploadRow("x", CloudUploadEntity.STATE_FAILED, error = "HTTP 403")),
        ).single()
        assertEquals(CloudActivityStatus.Failed, row.status)
        assertTrue(row.retryable)
        assertEquals("HTTP 403", row.error)
        assertNull(row.progress)
    }

    /** A missing copy is a problem, not a receipt — and re-uploadable. */
    @Test
    fun `a missing copy is retryable and flagged as a problem`() {
        val row = CloudActivityMerge.merge(
            ledger = listOf(ledgerRow("x", state = RemoteState.MISSING)),
            uploads = emptyList(),
        ).single()
        assertTrue(row.retryable)
        assertTrue(row.status.isProblem)
    }

    @Test
    fun `destination survives the merge`() {
        val rows = CloudActivityMerge.merge(
            ledger = listOf(
                ledgerRow("d", destination = CloudDestination.DRIVE),
                ledgerRow("p", destination = CloudDestination.PHOTOS),
            ),
            uploads = emptyList(),
        ).associateBy { it.contentUri }
        assertEquals(CloudDestination.DRIVE, rows.getValue("d").destination)
        assertEquals(CloudDestination.PHOTOS, rows.getValue("p").destination)
    }

    /** Bytes still moving are not bytes safely stored. */
    @Test
    fun `summary excludes in-flight and failed bytes from the stored total`() {
        val summary = CloudActivityMerge.summarize(
            CloudActivityMerge.merge(
                ledger = listOf(
                    ledgerRow("a", state = RemoteState.CONFIRMED, size = 100, verifiedAt = 7),
                    ledgerRow("b", state = RemoteState.RECORDED, size = 200),
                    ledgerRow("c", state = RemoteState.MISSING, size = 400),
                ),
                uploads = listOf(uploadRow("d", CloudUploadEntity.STATE_UPLOADING, size = 800)),
            )
        )
        assertEquals(300, summary.uploadedBytes)
        assertEquals(1, summary.inFlight)
        assertEquals(1, summary.verified)
        assertEquals(1, summary.uploaded)
        assertEquals(1, summary.problems)
        assertEquals(4, summary.total)
        assertEquals(7, summary.lastVerifiedAtMillis)
    }

    @Test
    fun `unknown persisted strings fall back instead of throwing`() {
        assertEquals(CloudDestination.DRIVE, CloudDestination.parse("SOMETHING_ELSE"))
        assertEquals(CloudDestination.DRIVE, CloudDestination.parse(null))
        assertEquals(RemoteState.RECORDED, RemoteState.parse("SOMETHING_ELSE"))
        assertEquals(RemoteState.RECORDED, RemoteState.parse(null))
    }

    /** A blank display name would otherwise render as an empty row. */
    @Test
    fun `blank display name falls back to the uri tail`() {
        val row = CloudActivityMerge.merge(
            ledger = listOf(ledgerRow("content://media/external/images/media/42").copy(displayName = "")),
            uploads = emptyList(),
        ).single()
        assertEquals("42", row.displayName)
    }
}
