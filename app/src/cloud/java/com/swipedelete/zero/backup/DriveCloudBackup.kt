package com.swipedelete.zero.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.swipedelete.zero.data.local.KeptFileEntity
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.ConnectionCheck
import com.swipedelete.zero.domain.backup.RemoteOriginal
import com.swipedelete.zero.domain.setup.AuthDiagnostic
import com.swipedelete.zero.photos.PhotosUploadWorker
import com.swipedelete.zero.photos.PhotosUploader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive implementation of [CloudBackup] (cloud flavor only).
 *
 * Auth: Google Sign-In with the non-sensitive `drive.file` scope — the app can
 * only see files it created itself, never the user's whole Drive. The OAuth
 * consent is validated against this app's package name + the committed debug
 * keystore's SHA-1 (see docs/DRIVE_BACKUP_SETUP.md), so no client secret ships
 * in the code.
 *
 * Uploads: plain Drive REST v3 multipart requests over HttpURLConnection into a
 * "SwipeDelete Zero Backup" folder. Every uploaded file is written to the
 * backup ledger, so each file is uploaded exactly once and later runs only
 * pick up newly kept files.
 */
@Singleton
class DriveCloudBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
) : CloudBackup {

    internal var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val running = AtomicBoolean(false)
    internal var backupJob: kotlinx.coroutines.Job? = null

    internal var getSignedInAccount: () -> Pair<String?, android.accounts.Account?> = {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            Pair(account?.email, account?.account)
        } catch (_: Throwable) {
            Pair(null, null)
        }
    }
    internal var getAuthToken: (android.accounts.Account) -> String = { androidAccount ->
        GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_FILE_SCOPE")
    }
    internal var clearAuthToken: (String) -> Unit = { token ->
        GoogleAuthUtil.clearToken(context, token)
    }
    internal var folderResolver: ((String) -> String)? = null
    internal var fileUploader: ((String, String, KeptFileEntity) -> String)? = null
    internal var clientSignOutAction: () -> Unit = {
        try {
            signInClient().signOut()
        } catch (_: Exception) {}
    }

    private val sessionLock = Any()

    internal val currentSessionId = java.util.concurrent.atomic.AtomicLong(0)
    internal var connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }
    internal val activeConnection = java.util.concurrent.atomic.AtomicReference<HttpURLConnection?>(null)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<BackupState>(initialState())
    override val state = _state

    private fun initialState(): BackupState {
        val (email, _) = try {
            getSignedInAccount()
        } catch (_: Throwable) {
            Pair(null, null)
        }
        return if (email != null) BackupState.Ready(email) else BackupState.SignedOut()
    }

    private fun signInClient() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // One consent covers Drive backup AND the swipe-up Photos archive.
            .requestScopes(
                Scope(DRIVE_FILE_SCOPE),
                Scope(PHOTOS_APPEND_SCOPE),
                Scope(PhotosUploader.PHOTOS_READ_SCOPE),
            )
            .build(),
    )

    override fun signInIntent(): Intent = signInClient().signInIntent

    override fun onSignInResult(data: Intent?) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            _state.value = BackupState.Ready(account.email ?: "Google account")
        } catch (e: ApiException) {
            // Decode the bare status code into a cause and a fix the setup
            // wizard can act on, instead of surfacing "code 10" to the user.
            val diagnostic = AuthDiagnostic.decode(e.statusCode)
            _state.value = BackupState.SignedOut(
                message = diagnostic.headline,
                diagnostic = diagnostic,
            )
        }
    }

    override fun signOut() = synchronized(sessionLock) {
        currentSessionId.incrementAndGet()
        backupJob?.cancel()
        backupJob = null
        running.set(false)
        activeConnection.getAndSet(null)?.disconnect()
        clientSignOutAction()
        _state.value = BackupState.SignedOut()
        try {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(PhotosUploadWorker.WORK_NAME)
        } catch (_: Exception) {}
        Unit
    }

    override fun backupNow() = synchronized(sessionLock) {
        if (!running.compareAndSet(false, true)) return
        val sessionId = currentSessionId.incrementAndGet()
        backupJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                runBackup(sessionId)
            } finally {
                synchronized(sessionLock) {
                    if (currentSessionId.get() == sessionId) running.set(false)
                }
            }
        }
        backupJob?.start()
        Unit
    }

    internal suspend fun runBackup(sessionId: Long = currentSessionId.get()) {
        val (email, androidAccount) = getSignedInAccount()
        if (email == null || androidAccount == null) {
            synchronized(sessionLock) {
            if (currentSessionId.get() == sessionId) {
                _state.value = BackupState.SignedOut("Connect Google Drive first.")
            }
            }
            return
        }
        synchronized(sessionLock) {
        if (currentSessionId.get() == sessionId && _state.value is BackupState.SignedOut) {
            _state.value = BackupState.Ready(email)
        }
        }

        val ownerJob = currentCoroutineContext()[kotlinx.coroutines.Job]
        fun isSessionActive(): Boolean {
            return currentSessionId.get() == sessionId &&
                _state.value !is BackupState.SignedOut &&
                ownerJob?.isActive != false &&
                getSignedInAccount().first == email
        }

        fun checkSessionActive() {
            if (!isSessionActive()) {
                synchronized(sessionLock) {
                if (currentSessionId.get() == sessionId) {
                    _state.value = BackupState.SignedOut()
                }
                }
                throw kotlinx.coroutines.CancellationException("Drive backup cancelled or session invalidated")
            }
        }

        checkSessionActive()

        val pending = backupRepository.pendingDriveBackup(email)
        if (pending.isEmpty()) {
            synchronized(sessionLock) {
            if (currentSessionId.get() == sessionId && _state.value !is BackupState.SignedOut) {
                _state.value = BackupState.Ready(email, "No new kept files are pending in this backup ledger.")
            }
            }
            return
        }

        try {
            checkSessionActive()
            var token = getAuthToken(androidAccount)
            checkSessionActive()
            val folderId = folderResolver?.invoke(token) ?: findOrCreateFolder(token, ::checkSessionActive)
            checkSessionActive()

            var done = 0
            var failed = 0
            synchronized(sessionLock) {
            if (currentSessionId.get() == sessionId) {
                _state.value = BackupState.Running(done, pending.size)
            }
            }

            for (file in pending) {
                checkSessionActive()
                val (currentEmail, _) = getSignedInAccount()
                if (currentEmail == null || currentEmail != email) {
                    synchronized(sessionLock) {
                    if (currentSessionId.get() == sessionId) {
                        _state.value = BackupState.SignedOut("Account disconnected during backup.")
                    }
                    }
                    return
                }

                var verifiedHash: String? = null
                val remoteId = try {
                    checkSessionActive()
                    fileUploader?.invoke(token, folderId, file) ?: uploadFile(token, folderId, file, ::checkSessionActive) { verifiedHash = it }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (error: Exception) {
                    if (error is HttpStatusException && error.code == 401) {
                        checkSessionActive()
                        val (recheckEmail, _) = getSignedInAccount()
                        if (recheckEmail == null || recheckEmail != email) {
                            synchronized(sessionLock) {
                            if (currentSessionId.get() == sessionId) {
                                _state.value = BackupState.SignedOut("Account disconnected during backup.")
                            }
                            }
                            return
                        }
                        clearAuthToken(token)
                        checkSessionActive()
                        token = getAuthToken(androidAccount)
                        checkSessionActive()
                        try {
                            fileUploader?.invoke(token, folderId, file) ?: uploadFile(token, folderId, file, ::checkSessionActive) { verifiedHash = it }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (retryError: Exception) {
                            failed++
                            null
                        }
                    } else {
                        failed++
                        null
                    }
                }

                checkSessionActive()
                if (remoteId != null) {
                    val (checkEmail, _) = getSignedInAccount()
                    if (checkEmail == null || checkEmail != email) {
                        synchronized(sessionLock) {
                        if (currentSessionId.get() == sessionId) {
                            _state.value = BackupState.SignedOut("Account disconnected during backup.")
                        }
                        }
                        return
                    }
                    // Fake uploader tests intentionally have no byte proof. Real uploads
                    // publish an account-scoped receipt only after the remote download
                    // and the unchanged local original match.
                    if (verifiedHash != null) {
                        backupRepository.markVerifiedDriveBackup(file, email, remoteId, verifiedHash)
                    } else {
                        // Deterministic fake uploader seam for the existing
                        // session tests; production always returns byte proof.
                        backupRepository.markBackedUp(file, remoteId)
                    }
                    done++
                }

                checkSessionActive()
                synchronized(sessionLock) {
                if (currentSessionId.get() == sessionId) {
                    _state.value = BackupState.Running(done, pending.size)
                }
                }
            }

            checkSessionActive()
            val (finalEmail, _) = getSignedInAccount()
            if (finalEmail == null || finalEmail != email) {
                synchronized(sessionLock) {
                if (currentSessionId.get() == sessionId) {
                    _state.value = BackupState.SignedOut("Account disconnected during backup.")
                }
                }
                return
            }

            synchronized(sessionLock) {
            if (currentSessionId.get() == sessionId) {
                _state.value = BackupState.Ready(
                    email,
                    if (failed == 0) "Backed up $done file${if (done == 1) "" else "s"}."
                    else "Backed up $done, $failed failed — run again to retry.",
                )
            }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            synchronized(sessionLock) {
            if (currentSessionId.get() == sessionId) {
                _state.value = BackupState.SignedOut()
            }
            }
            throw e
        } catch (e: UserRecoverableAuthException) {
            synchronized(sessionLock) {
                if (currentSessionId.get() == sessionId) {
                    _state.value = BackupState.SignedOut("Google needs re-consent — connect again.")
                    clientSignOutAction()
                }
            }
        } catch (e: Exception) {
            synchronized(sessionLock) {
            if (currentSessionId.get() == sessionId) {
                val (checkEmail, _) = getSignedInAccount()
                if (checkEmail == null || checkEmail != email || _state.value is BackupState.SignedOut) {
                    _state.value = BackupState.SignedOut("Account disconnected during backup.")
                } else {
                    _state.value = BackupState.Ready(email, "Backup failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            }
        }
    }

    /**
     * Probe the real APIs so the wizard can prove the setup works.
     *
     * Drive is checked with an actual `about` request — that only succeeds when
     * the client is registered, consent was granted and the API is enabled.
     * Photos is checked with a read-only list of app-created media. A token
     * alone does not prove the Photos API is enabled or answering requests.
     */
    override suspend fun verifyConnection(): ConnectionCheck = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val androidAccount = account?.account
        if (account == null || androidAccount == null) {
            return@withContext ConnectionCheck(
                signedIn = false,
                diagnostic = AuthDiagnostic.decode(AuthDiagnostic.SIGN_IN_REQUIRED),
                message = "Connect your Google account to check backup access.",
            )
        }
        val email = account.email

        var driveOk = false
        var photosOk = false
        var diagnostic: AuthDiagnostic? = null
        val notes = mutableListOf<String>()

        try {
            val token = GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_FILE_SCOPE")
            httpGet("https://www.googleapis.com/drive/v3/about?fields=user", token)
            driveOk = true
        } catch (e: UserRecoverableAuthException) {
            diagnostic = AuthDiagnostic.decode(AuthDiagnostic.SIGN_IN_REQUIRED)
            notes += "Drive needs consent again."
        } catch (e: HttpStatusException) {
            diagnostic = AuthDiagnostic.decode(
                if (e.code == 403) AuthDiagnostic.API_NOT_CONNECTED else AuthDiagnostic.DEVELOPER_ERROR
            )
            notes += "Drive API returned HTTP ${e.code}."
        } catch (e: Exception) {
            notes += "Drive check failed: ${e.message ?: e.javaClass.simpleName}"
        }

        try {
            GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$PHOTOS_APPEND_SCOPE")
            val readToken = GoogleAuthUtil.getToken(
                context, androidAccount, "oauth2:${PhotosUploader.PHOTOS_READ_SCOPE}"
            )
            httpGet("https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=1", readToken)
            photosOk = true
        } catch (e: UserRecoverableAuthException) {
            diagnostic = diagnostic ?: AuthDiagnostic.decode(AuthDiagnostic.SIGN_IN_REQUIRED)
            notes += "Photos scope not granted — reconnect and accept the Photos permission."
        } catch (e: Exception) {
            diagnostic = diagnostic ?: AuthDiagnostic.decode(AuthDiagnostic.API_NOT_CONNECTED)
            notes += "Photos scope check failed: ${e.message ?: e.javaClass.simpleName}"
        }

        val message = when {
            driveOk && photosOk ->
                "Signed in as $email. Drive and Google Photos answered live requests. " +
                    "Cleanup is unavailable in this test build. Your originals stay on this device."
            notes.isEmpty() -> "Connected as $email."
            else -> notes.joinToString(" ")
        }

        ConnectionCheck(
            signedIn = true,
            accountEmail = email,
            driveOk = driveOk,
            photosOk = photosOk,
            diagnostic = diagnostic,
            message = message,
        )
    }

    override suspend fun availableOriginals(): List<RemoteOriginal> = withContext(Dispatchers.IO) {
        val (email, account) = getSignedInAccount()
        if (email.isNullOrBlank() || account == null) return@withContext emptyList()
        val session = currentSessionId.get()
        val ownerJob = currentCoroutineContext()[kotlinx.coroutines.Job]
        fun guard() {
            if (ownerJob?.isActive == false ||
                session != currentSessionId.get() || getSignedInAccount().first != email) {
                throw kotlinx.coroutines.CancellationException("Google account changed during restore search")
            }
        }
        guard()
        val token = getAuthToken(account)
        guard()
        val folderId = findFolder(token, ::guard) ?: return@withContext emptyList()
        val query = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
        val found = mutableListOf<RemoteOriginal>()
        val pages = mutableSetOf<String>()
        var pageToken: String? = null
        do {
            guard()
            val pageArg = pageToken?.let { "&pageToken=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&pageSize=100" +
                "&fields=nextPageToken,files(id,name,mimeType,size,appProperties)$pageArg"
            val response = JSONObject(httpRequest(url, token, "GET", null, ::guard))
            val files = response.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val item = files.getJSONObject(i)
                val props = item.optJSONObject("appProperties") ?: continue
                val sha = props.optString("originalSha256")
                val size = props.optString("originalSize").toLongOrNull() ?: continue
                val id = item.optString("id")
                if (props.optString("swipeRiseVersion") != "1" ||
                    !sha.matches(Regex("[0-9a-f]{64}")) || size <= 0 ||
                    id.isBlank() || !id.matches(Regex("[A-Za-z0-9_-]+")) ||
                    item.optLong("size", -1) != size) continue
                found += RemoteOriginal(
                    remoteId = id,
                    name = item.optString("name").take(200).ifBlank { "Original" },
                    mimeType = item.optString("mimeType").ifBlank { "application/octet-stream" },
                    sizeBytes = size,
                    sha256 = sha,
                    accountId = email,
                )
            }
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (pageToken != null && (!pages.add(pageToken!!) || pages.size >= 100)) {
                error("Drive backup inventory pagination did not finish")
            }
        } while (pageToken != null)
        guard()
        found.distinctBy { it.remoteId }
    }

    override suspend fun restoreOriginal(original: RemoteOriginal, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        val (email, account) = getSignedInAccount()
        if (email.isNullOrBlank() || account == null || original.accountId != email ||
            !original.remoteId.matches(Regex("[A-Za-z0-9_-]+")) ||
            !original.sha256.matches(Regex("[0-9a-f]{64}")) || original.sizeBytes <= 0) return@withContext false
        val session = currentSessionId.get()
        val ownerJob = currentCoroutineContext()[kotlinx.coroutines.Job]
        fun guard() {
            if (ownerJob?.isActive == false ||
                session != currentSessionId.get() || getSignedInAccount().first != email) {
                throw kotlinx.coroutines.CancellationException("Google account changed during restore")
            }
        }
        // Keep an untrusted download away from the user's chosen document until
        // its bytes pass verification; refuse if private scratch cannot fit it.
        val available = context.cacheDir.usableSpace
        if (available <= original.sizeBytes || available - original.sizeBytes <= 8L * 1024 * 1024) {
            return@withContext false
        }
        val temp = java.io.File.createTempFile("swiperise-restore-", ".bin", context.cacheDir)
        try {
            guard()
            val token = getAuthToken(account)
            guard()
            // The UI's cached inventory is never authoritative at restore time.
            val metadata = JSONObject(httpRequest(
                "https://www.googleapis.com/drive/v3/files/${original.remoteId}?fields=id,size,mimeType,appProperties,trashed",
                token, "GET", null, ::guard,
            ))
            val props = metadata.optJSONObject("appProperties") ?: return@withContext false
            if (metadata.optBoolean("trashed") || metadata.optString("id") != original.remoteId ||
                metadata.optLong("size", -1) != original.sizeBytes ||
                metadata.optString("mimeType") != original.mimeType ||
                props.optString("swipeRiseVersion") != "1" ||
                props.optString("originalSize") != original.sizeBytes.toString() ||
                props.optString("originalSha256") != original.sha256) return@withContext false

            val connection = connectionFactory(
                "https://www.googleapis.com/drive/v3/files/${original.remoteId}?alt=media"
            ).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Cache-Control", "no-cache")
                useCaches = false
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            synchronized(sessionLock) { guard(); activeConnection.set(connection) }
            try {
                guard()
                if (connection.responseCode != 200) return@withContext false
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val total = connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        copyStreamWithCancellation(java.security.DigestInputStream(input, digest),
                            output, ::guard, original.sizeBytes)
                    }
                }
                if (total != original.sizeBytes ||
                    digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } != original.sha256) {
                    return@withContext false
                }
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
            guard()
            val output = context.contentResolver.openOutputStream(destination, "w") ?: return@withContext false
            output.use { temp.inputStream().use { input ->
                if (copyStreamWithCancellation(input, it, ::guard, original.sizeBytes) != original.sizeBytes) return@withContext false
            } }
            guard()
            val restored = context.contentResolver.openInputStream(destination)?.use {
                digestStream(it, original.sizeBytes, ::guard)
            } ?: return@withContext false
            guard()
            restored.joinToString("") { "%02x".format(it.toInt() and 0xff) } == original.sha256
        } finally {
            temp.delete()
        }
    }

    /** Find the app folder without creating a new folder during restore browsing. */
    private fun findFolder(token: String, guard: () -> Unit): String? {
        guard()
        val query = URLEncoder.encode(
            "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false", "UTF-8",
        )
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)&spaces=drive"
        val files = JSONObject(httpRequest(listUrl, token, "GET", null, guard)).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    /** Returns the id of the backup folder, creating it on first run. */
    private fun findOrCreateFolder(token: String, guard: () -> Unit): String {
        guard()
        val query = URLEncoder.encode(
            "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false",
            "UTF-8",
        )
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)&spaces=drive"
        val listResponse = JSONObject(httpRequest(listUrl, token, "GET", null, guard))
        guard()
        val files = listResponse.optJSONArray("files") ?: JSONArray()
        if (files.length() > 0) return files.getJSONObject(0).getString("id")

        val body = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .toString()
        val created = JSONObject(
            httpRequest("https://www.googleapis.com/drive/v3/files?fields=id", token, "POST", body, guard)
        )
        return created.getString("id")
    }

    /** Multipart upload of one file; returns the created Drive file id. */
    private fun uploadFile(token: String, folderId: String, file: KeptFileEntity, guard: () -> Unit, onVerified: (String) -> Unit): String {
        guard()
        if (file.sizeBytes > 5L * 1024 * 1024) {
            return uploadLargeFile(token, folderId, file, guard, onVerified)
        }
        val localHashBeforeUpload = context.contentResolver.openInputStream(Uri.parse(file.contentUri))?.use {
            digestStream(it, file.sizeBytes, guard)
        } ?: error("Local original is unreadable.")
        val hashHex = localHashBeforeUpload.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        // Keep the manifest on the remote object itself so a fresh install can
        // discover and restore it without the device's Room database.
        val metadata = JSONObject()
            .put("name", file.displayName)
            .put("parents", JSONArray().put(folderId))
            .put("appProperties", JSONObject()
                .put("swipeRiseVersion", "1")
                .put("originalSha256", hashHex)
                .put("originalSize", file.sizeBytes.toString()))
            .toString()

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
        val connection = connectionFactory(url.toString()).apply {
            requestMethod = "POST"
            doOutput = true
            setChunkedStreamingMode(0)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
            connectTimeout = 30_000
            readTimeout = 120_000
        }

        synchronized(sessionLock) {
            guard()
            activeConnection.set(connection)
        }
        val uploadedDigest = java.security.MessageDigest.getInstance("SHA-256")
        try {
            connection.outputStream.use { out ->
                out.writeAscii("--$BOUNDARY\r\n")
                out.writeAscii("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                out.write(metadata.toByteArray(Charsets.UTF_8))
                out.writeAscii("\r\n--$BOUNDARY\r\n")
                out.writeAscii("Content-Type: ${file.mimeType.ifBlank { "application/octet-stream" }}\r\n\r\n")

                val input = context.contentResolver.openInputStream(Uri.parse(file.contentUri))
                    ?: throw IllegalStateException("File unreadable: ${file.displayName}")
                input.use {
                    val counted = java.security.DigestInputStream(it, uploadedDigest)
                    val uploadedBytes = copyStreamWithCancellation(counted, out, guard, file.sizeBytes)
                    check(uploadedBytes == file.sizeBytes) { "Local file changed size; backup was not verified." }
                }

                out.writeAscii("\r\n--$BOUNDARY--\r\n")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw HttpStatusException(code, error ?: "HTTP $code")
            }
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val remoteId = JSONObject(responseText).getString("id")
            check(remoteId.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid Drive file ID" }
            val expectedHash = uploadedDigest.digest()
            check(java.security.MessageDigest.isEqual(localHashBeforeUpload, expectedHash)) {
                "Local file changed while uploading; original retained."
            }
            // Release the upload socket before opening the independent download.
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
            verifyDownloadedOriginal(token, remoteId, file.sizeBytes, expectedHash, guard)
            // A file replaced while uploading must not be labelled backed up.
            val localHash = context.contentResolver.openInputStream(Uri.parse(file.contentUri))?.use {
                digestStream(it, file.sizeBytes, guard)
            } ?: error("Local original is no longer readable.")
            check(java.security.MessageDigest.isEqual(expectedHash, localHash)) {
                "Local file changed during backup; review and retry."
            }
            guard()
            onVerified(expectedHash.joinToString("") { "%02x".format(it.toInt() and 0xff) })
            return remoteId
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    /** Drive recommends a resumable session for files over 5 MiB. This first
     * version sends one bounded PUT; transient interrupted sessions are retried
     * as new uploads and are not yet persisted across process death. */
    private fun uploadLargeFile(
        token: String, folderId: String, file: KeptFileEntity, guard: () -> Unit,
        onVerified: (String) -> Unit,
    ): String {
        guard()
        val sourceHash = context.contentResolver.openInputStream(Uri.parse(file.contentUri))?.use {
            digestStream(it, file.sizeBytes, guard)
        } ?: error("Local original is unreadable.")
        val hashHex = sourceHash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val metadata = JSONObject()
            .put("name", file.displayName)
            .put("parents", JSONArray().put(folderId))
            .put("mimeType", file.mimeType.ifBlank { "application/octet-stream" })
            .put("appProperties", JSONObject()
                .put("swipeRiseVersion", "1")
                .put("originalSha256", hashHex)
                .put("originalSize", file.sizeBytes.toString()))
        val init = connectionFactory("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", file.mimeType.ifBlank { "application/octet-stream" })
            setRequestProperty("X-Upload-Content-Length", file.sizeBytes.toString())
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        synchronized(sessionLock) { guard(); activeConnection.set(init) }
        val sessionUrl = try {
            guard()
            init.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
            val code = init.responseCode
            if (code != 200) throw HttpStatusException(code, "Drive upload session failed")
            init.getHeaderField("Location") ?: error("Drive did not return an upload session")
        } finally {
            activeConnection.compareAndSet(init, null)
            init.disconnect()
        }
        val session = URL(sessionUrl)
        // Location contains a bearer-capable upload ID; never send media or
        // Authorization to an arbitrary host supplied in a response header.
        check(session.protocol == "https" && session.host == "www.googleapis.com" &&
            session.port == -1 && session.path == "/upload/drive/v3/files") {
            "Unexpected Drive upload session destination"
        }
        guard()
        val connection = connectionFactory(sessionUrl).apply {
            requestMethod = "PUT"
            doOutput = true
            setFixedLengthStreamingMode(file.sizeBytes)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", file.mimeType.ifBlank { "application/octet-stream" })
            connectTimeout = 30_000
            readTimeout = 120_000
        }
        synchronized(sessionLock) { guard(); activeConnection.set(connection) }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val remoteId = try {
            val input = context.contentResolver.openInputStream(Uri.parse(file.contentUri))
                ?: error("Local original is unreadable.")
            input.use { source -> connection.outputStream.use { output ->
                val total = copyStreamWithCancellation(java.security.DigestInputStream(source, digest),
                    output, guard, file.sizeBytes)
                check(total == file.sizeBytes) { "Local original changed size." }
            } }
            guard()
            check(java.security.MessageDigest.isEqual(sourceHash, digest.digest())) {
                "Local original changed during upload."
            }
            val code = connection.responseCode
            if (code !in 200..201) throw HttpStatusException(code, "Drive upload failed")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getString("id")
                .also { check(it.matches(Regex("[A-Za-z0-9_-]+"))) }
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
        guard()
        verifyDownloadedOriginal(token, remoteId, file.sizeBytes, sourceHash, guard)
        val stillLocal = context.contentResolver.openInputStream(Uri.parse(file.contentUri))?.use {
            digestStream(it, file.sizeBytes, guard)
        } ?: error("Local original no longer readable.")
        check(java.security.MessageDigest.isEqual(sourceHash, stillLocal)) {
            "Local original changed during backup."
        }
        guard()
        onVerified(hashHex)
        return remoteId
    }

    private fun copyStreamWithCancellation(
        input: java.io.InputStream, out: OutputStream, guard: () -> Unit, maxBytes: Long = Long.MAX_VALUE,
    ): Long {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var total = 0L
        while (input.read(buffer).also { bytesRead = it } >= 0) {
            guard()
            total += bytesRead
            check(total <= maxBytes) { "File exceeds the expected original size." }
            out.write(buffer, 0, bytesRead)
        }
        guard()
        return total
    }

    /** Fresh authenticated download, never metadata or a local cache, before ledger write. */
    private fun verifyDownloadedOriginal(
        token: String, remoteId: String, size: Long, expectedHash: ByteArray, guard: () -> Unit,
    ) {
        guard()
        val connection = connectionFactory("https://www.googleapis.com/drive/v3/files/$remoteId?alt=media").apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
            useCaches = false
            instanceFollowRedirects = false
            connectTimeout = 30_000
            readTimeout = 120_000
        }
        synchronized(sessionLock) {
            guard()
            activeConnection.set(connection)
        }
        try {
            guard()
            val code = connection.responseCode
            if (code != 200) throw HttpStatusException(code, "Drive download verification failed: HTTP $code")
            val actualHash = connection.inputStream.use { digestStream(it, size, guard) }
            check(java.security.MessageDigest.isEqual(expectedHash, actualHash)) {
                "Drive download did not match the original. Local file retained."
            }
            guard()
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun digestStream(input: java.io.InputStream, expectedSize: Long, guard: () -> Unit): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            guard()
            val count = input.read(buffer)
            if (count < 0) break
            guard()
            total += count
            check(total <= expectedSize) { "Backup size exceeds the expected original." }
            digest.update(buffer, 0, count)
        }
        guard()
        check(total == expectedSize) { "Backup download is incomplete." }
        return digest.digest()
    }

    private fun httpGet(urlString: String, token: String): String =
        httpRequest(urlString, token, method = "GET", body = null)

    private fun httpPostJson(urlString: String, token: String, body: String): String =
        httpRequest(urlString, token, method = "POST", body = body)

    private fun httpRequest(urlString: String, token: String, method: String, body: String?, guard: () -> Unit = {}): String {
        guard()
        val connection = connectionFactory(urlString).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 30_000
            readTimeout = 60_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        synchronized(sessionLock) {
            guard()
            activeConnection.set(connection)
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw HttpStatusException(code, error ?: "HTTP $code")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return text
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun OutputStream.writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

    internal class HttpStatusException(val code: Int, message: String) : Exception(message)

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val PHOTOS_APPEND_SCOPE = PhotosUploader.PHOTOS_APPEND_SCOPE
        const val FOLDER_NAME = "SwipeDelete Zero Backup"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val BOUNDARY = "sdz-backup-boundary-7f2a"
    }
}
