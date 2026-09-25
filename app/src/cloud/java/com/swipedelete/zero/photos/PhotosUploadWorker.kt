package com.swipedelete.zero.photos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.AppDatabase
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.data.local.StagedFileDao
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.domain.backup.UploadEvent
import com.swipedelete.zero.domain.backup.UploadReducer
import com.swipedelete.zero.domain.model.MediaType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Drains the cloud_uploads queue: resumable-uploads each file to Google
 * Photos, then runs the batchCreate verification handshake. Every event is
 * reduced into Room, so a process death mid-20-GB-file resumes at the
 * server-acked offset (`query` command) instead of restarting.
 *
 * Runs as a dataSync foreground service — WorkManager's ~10-minute
 * background budget is nowhere near enough for multi-GB uploads.
 *
 * Safety contract: this worker NEVER deletes anything. On VERIFIED it only
 * writes the backup ledger row and stages the file into the ordinary Safety
 * Staging queue — deletion still goes through the user-visible
 * MediaStore confirmation dialog like every other staged file.
 */
@HiltWorker
class PhotosUploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val uploadDao: CloudUploadDao,
    private val backedUpFileDao: BackedUpFileDao,
    private val stagedFileDao: StagedFileDao,
    private val uploader: PhotosUploader,
    private val database: AppDatabase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(foregroundInfo("Preparing…"))

        val initialAccount = GoogleSignIn.getLastSignedInAccount(appContext)?.account
            ?: return@withContext Result.failure()
        val boundAccountName = initialAccount.name
        var authToken = try {
            GoogleAuthUtil.getToken(appContext, initialAccount, "oauth2:${PhotosUploader.PHOTOS_APPEND_SCOPE}")
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        uploadDao.verifiedWithoutLedger().forEach { onVerified(it) }

        var authRetries = 0
        val maxAuthRetries = 2

        while (true) {
            if (isStopped) return@withContext Result.retry()
            // Bound to stable account: do not cross-use credentials if user switched accounts.
            val currentAccount = GoogleSignIn.getLastSignedInAccount(appContext)?.account
            if (currentAccount == null || currentAccount.name != boundAccountName) {
                return@withContext Result.retry()
            }

            val row = uploadDao.nextPending() ?: break
            setForeground(foregroundInfo(row.displayName))

            val outcome = try {
                processRow(row, authToken, currentAccount)
                authRetries = 0
                RowOutcome.DONE
            } catch (e: PhotosUploader.HttpStatusException) {
                if (e.code == 401) {
                    authRetries++
                    if (authRetries > maxAuthRetries) {
                        applyFailure(row, 401, "Authentication failed after token refresh")
                        RowOutcome.DONE
                    } else {
                        // Append token expired mid-run: clear, refresh, let the loop retry the row.
                        GoogleAuthUtil.clearToken(appContext, authToken)
                        authToken = try {
                            GoogleAuthUtil.getToken(
                                appContext, currentAccount, "oauth2:${PhotosUploader.PHOTOS_APPEND_SCOPE}"
                            )
                        } catch (_: Exception) {
                            return@withContext Result.retry()
                        }
                        RowOutcome.RETRY_NOW
                    }
                } else {
                    applyFailure(row, if (e.code == 404 && row.mediaItemId != null) null else e.code,
                        e.message ?: "HTTP ${e.code}")
                }
            } catch (e: IOException) {
                applyFailure(row, null, e.message ?: "network error")
            } catch (e: Exception) {
                applyFailure(row, 400, e.message ?: e.javaClass.simpleName)
            }

            if (outcome == RowOutcome.BACKOFF) return@withContext Result.retry()
        }
        Result.success()
    }

    private enum class RowOutcome { DONE, RETRY_NOW, BACKOFF }

    /** Reduce a failure into Room; retryable rows trigger WorkManager backoff. */
    private suspend fun applyFailure(row: CloudUploadEntity, code: Int?, message: String): RowOutcome {
        val fresh = uploadDao.get(row.contentUri) ?: return RowOutcome.DONE
        val reduced = UploadReducer.reduce(
            fresh, UploadEvent.Failed(code, message), System.currentTimeMillis()
        )
        uploadDao.upsert(reduced)
        return if (reduced.state == CloudUploadEntity.STATE_QUEUED) RowOutcome.BACKOFF else RowOutcome.DONE
    }

    private suspend fun processRow(start: CloudUploadEntity, authToken: String, account: android.accounts.Account) {
        var row = start
        val uri = Uri.parse(row.contentUri)

        // The Raw-Size header must be the true byte count — MediaStore's cached
        // size can be stale for freshly-written videos.
        val actualSize = appContext.contentResolver.openAssetFileDescriptor(uri, "r")
            ?.use { it.length } ?: row.sizeBytes
        if (actualSize > 0 && actualSize != row.sizeBytes) {
            row = row.copy(sizeBytes = actualSize)
            uploadDao.upsert(row)
        }
        if (row.sizeBytes <= 0) throw IOException("Cannot back up an empty or unreadable file")
        chunkGranularity = 0

        // Upload phase (skipped when a crashed run already holds a token).
        if (row.uploadToken == null) {
            var uploadUrl = row.uploadUrl
            var offset: Long = 0
            if (uploadUrl == null) {
                val session = uploader.startSession(
                    authToken, row.mimeType.ifBlank { "application/octet-stream" }, row.sizeBytes
                )
                uploadUrl = session.uploadUrl
                row = reduceAndSave(row, UploadEvent.SessionStarted(session.uploadUrl))
                offset = 0
                // Granularity is a session detail, carried in-memory for chunk
                // sizing; resumed sessions fall back to the 8 MiB default,
                // which is a multiple of the API's 256 KiB granularity.
                chunkGranularity = session.chunkGranularityBytes
            } else {
                val queryResult = uploader.querySession(authToken, uploadUrl)
                if (queryResult.status == "final") {
                    if (queryResult.uploadToken != null) {
                        // Recovered finalized upload token from query response!
                        row = reduceAndSave(row, UploadEvent.Finalized(queryResult.uploadToken))
                        offset = row.sizeBytes
                    } else {
                        // Session finalized on server but upload token is lost:
                        // safely restart session from scratch to avoid crash loop or fabricated token.
                        val session = uploader.startSession(
                            authToken, row.mimeType.ifBlank { "application/octet-stream" }, row.sizeBytes
                        )
                        uploadUrl = session.uploadUrl
                        row = reduceAndSave(row, UploadEvent.SessionReset(session.uploadUrl))
                        offset = 0
                        chunkGranularity = session.chunkGranularityBytes
                    }
                } else {
                    offset = queryResult.offset
                    if (offset >= row.sizeBytes) {
                        // Server claims all bytes received but session was not finalized:
                        // safely restart session to obtain a valid finalize response.
                        val session = uploader.startSession(
                            authToken, row.mimeType.ifBlank { "application/octet-stream" }, row.sizeBytes
                        )
                        uploadUrl = session.uploadUrl
                        row = reduceAndSave(row, UploadEvent.SessionReset(session.uploadUrl))
                        offset = 0
                        chunkGranularity = session.chunkGranularityBytes
                    } else {
                        row = reduceAndSave(row, UploadEvent.ChunkAcked(offset))
                    }
                }
            }

            if (row.uploadToken == null) {
                val chunkSize = UploadReducer.chunkSizeFor(chunkGranularity)
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    skipFully(input, offset)
                    val buffer = ByteArray(chunkSize)
                    while (offset < row.sizeBytes) {
                        if (isStopped) throw IOException("worker stopped")
                        val toRead = minOf(chunkSize.toLong(), row.sizeBytes - offset).toInt()
                        readFully(input, buffer, toRead)
                        val isLast = offset + toRead >= row.sizeBytes
                        val token = uploader.uploadChunk(authToken, checkNotNull(uploadUrl), buffer, toRead, offset, isLast)
                        offset += toRead
                        row = if (isLast) {
                            reduceAndSave(row, UploadEvent.Finalized(checkNotNull(token)))
                        } else {
                            reduceAndSave(row, UploadEvent.ChunkAcked(offset))
                        }
                        setForeground(
                            foregroundInfo("${row.displayName} · ${(offset * 100 / row.sizeBytes)}%")
                        )
                    }
                } ?: throw IOException("File unreadable: ${row.displayName}")
            }
        }

        // Verification handshake — the ONLY path to VERIFIED.
        if (row.mediaItemId.isNullOrBlank()) {
            val mediaItemId = uploader.batchCreate(
                authToken, checkNotNull(row.uploadToken), row.displayName
            )
            row = reduceAndSave(row, UploadEvent.Created(mediaItemId))
        }

        // Independent read-token authentication with scoped 401 handling.
        val remote = run {
            var rToken = try {
                GoogleAuthUtil.getToken(appContext, account, "oauth2:${PhotosUploader.PHOTOS_READ_SCOPE}")
            } catch (e: Exception) {
                throw IOException("Could not authenticate Google Photos readback", e)
            }
            try {
                uploader.getMediaItem(rToken, checkNotNull(row.mediaItemId))
            } catch (e: PhotosUploader.HttpStatusException) {
                if (e.code == 401) {
                    // Clear and refresh READ token specifically, never append token!
                    GoogleAuthUtil.clearToken(appContext, rToken)
                    rToken = try {
                        GoogleAuthUtil.getToken(appContext, account, "oauth2:${PhotosUploader.PHOTOS_READ_SCOPE}")
                    } catch (ex: Exception) {
                        throw IOException("Could not refresh Google Photos read token", ex)
                    }
                    uploader.getMediaItem(rToken, checkNotNull(row.mediaItemId))
                } else throw e
            }
        }

        if (remote.id != row.mediaItemId || remote.mimeType != row.mimeType ||
            remote.filename != row.displayName || !remote.productUrl.startsWith("https://")) {
            throw IOException("Google Photos did not confirm matching media and link")
        }
        row = reduceAndSave(row, UploadEvent.RemoteVerified)
        if (row.state == CloudUploadEntity.STATE_VERIFIED) {
            onVerified(row)
        }
    }

    private var chunkGranularity: Long = 0

    private suspend fun reduceAndSave(row: CloudUploadEntity, event: UploadEvent): CloudUploadEntity {
        val reduced = UploadReducer.reduce(row, event, System.currentTimeMillis())
        uploadDao.upsert(reduced)
        return reduced
    }

    /** Ledger + staging — never a direct delete. */
    private suspend fun onVerified(row: CloudUploadEntity) {
        val now = System.currentTimeMillis()
        database.withTransaction {
        backedUpFileDao.insert(
            BackedUpFileEntity(
                contentUri = row.contentUri,
                sizeBytes = row.sizeBytes,
                remoteId = "photos:${row.mediaItemId}",
                uploadedAtMillis = now,
            )
        )
        stagedFileDao.stage(
            StagedFileEntity(
                contentUri = row.contentUri,
                displayName = row.displayName,
                mimeType = row.mimeType,
                mediaType = if (row.mimeType.startsWith("video/")) MediaType.VIDEO.name else MediaType.IMAGE.name,
                sizeBytes = row.sizeBytes,
                relativePath = null,
                stagedAtMillis = now,
                sourceDeckId = VERIFIED_SOURCE_DECK,
            )
        )
        }
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) throw IOException("Unable to seek to resume offset")
            remaining -= skipped
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int) {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) throw IOException("Unexpected end of stream")
            read += n
        }
    }

    private fun foregroundInfo(text: String): ForegroundInfo {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Google Photos uploads", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading to Google Photos")
            .setContentText(text)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val WORK_NAME = "photos-upload"
        const val VERIFIED_SOURCE_DECK = com.swipedelete.zero.domain.backup.PhotosArchive.VERIFIED_SOURCE_DECK
        private const val CHANNEL_ID = "photos-upload"
        private const val NOTIFICATION_ID = 42
    }
}
