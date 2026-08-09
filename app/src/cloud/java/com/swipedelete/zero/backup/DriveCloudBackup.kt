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
import com.swipedelete.zero.domain.setup.AuthDiagnostic
import com.swipedelete.zero.photos.PhotosUploader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<BackupState>(initialState())
    override val state = _state

    private fun initialState(): BackupState {
        val email = GoogleSignIn.getLastSignedInAccount(context)?.email
        return if (email != null) BackupState.Ready(email) else BackupState.SignedOut()
    }

    private fun signInClient() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // One consent covers Drive backup AND the swipe-up Photos archive.
            .requestScopes(Scope(DRIVE_FILE_SCOPE), Scope(PHOTOS_APPEND_SCOPE))
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

    override fun signOut() {
        signInClient().signOut()
        _state.value = BackupState.SignedOut()
    }

    override fun backupNow() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                runBackup()
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun runBackup() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val androidAccount = account?.account
        if (account == null || androidAccount == null) {
            _state.value = BackupState.SignedOut("Connect Google Drive first.")
            return
        }
        val email = account.email ?: "Google account"

        val pending = backupRepository.pendingBackup()
        if (pending.isEmpty()) {
            _state.value = BackupState.Ready(email, "Everything is already backed up.")
            return
        }

        try {
            var token = GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_FILE_SCOPE")
            val folderId = findOrCreateFolder(token)

            var done = 0
            var failed = 0
            _state.value = BackupState.Running(done, pending.size)

            for (file in pending) {
                val result = runCatching { uploadFile(token, folderId, file) }
                    .recoverCatching { error ->
                        if (error is HttpStatusException && error.code == 401) {
                            // Token expired mid-run: clear, refresh, retry once.
                            GoogleAuthUtil.clearToken(context, token)
                            token = GoogleAuthUtil.getToken(
                                context, androidAccount, "oauth2:$DRIVE_FILE_SCOPE"
                            )
                            uploadFile(token, folderId, file)
                        } else {
                            throw error
                        }
                    }

                result.fold(
                    onSuccess = { remoteId ->
                        backupRepository.markBackedUp(file, remoteId)
                        done++
                    },
                    onFailure = { failed++ },
                )
                _state.value = BackupState.Running(done, pending.size)
            }

            _state.value = BackupState.Ready(
                email,
                if (failed == 0) "Backed up $done file${if (done == 1) "" else "s"}."
                else "Backed up $done, $failed failed — run again to retry.",
            )
        } catch (e: UserRecoverableAuthException) {
            _state.value = BackupState.SignedOut("Google needs re-consent — connect again.")
            signInClient().signOut()
        } catch (e: Exception) {
            _state.value = BackupState.Ready(email, "Backup failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Probe the real APIs so the wizard can prove the setup works.
     *
     * Drive is checked with an actual `about` request — that only succeeds when
     * the client is registered, consent was granted and the API is enabled.
     * Photos has no read endpoint under the upload-only `appendonly` scope, so
     * the strongest side-effect-free check is minting a token for that scope,
     * which proves the scope was granted to this OAuth client. Uploads are not
     * attempted here — a probe must never create content in someone's library.
     */
    override suspend fun verifyConnection(): ConnectionCheck = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val androidAccount = account?.account
        if (account == null || androidAccount == null) {
            return@withContext ConnectionCheck(
                signedIn = false,
                diagnostic = AuthDiagnostic.decode(AuthDiagnostic.SIGN_IN_REQUIRED),
                message = "Not connected yet — finish step 5 first.",
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
                "Signed in as $email. Drive answered a live request and the Photos " +
                    "upload scope is granted, so swipe-up archiving will work."
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

    /** Returns the id of the backup folder, creating it on first run. */
    private fun findOrCreateFolder(token: String): String {
        val query = URLEncoder.encode(
            "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false",
            "UTF-8",
        )
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)&spaces=drive"
        val listResponse = JSONObject(httpGet(listUrl, token))
        val files = listResponse.optJSONArray("files") ?: JSONArray()
        if (files.length() > 0) return files.getJSONObject(0).getString("id")

        val body = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .toString()
        val created = JSONObject(
            httpPostJson("https://www.googleapis.com/drive/v3/files?fields=id", token, body)
        )
        return created.getString("id")
    }

    /** Multipart upload of one file; returns the created Drive file id. */
    private fun uploadFile(token: String, folderId: String, file: KeptFileEntity): String {
        val metadata = JSONObject()
            .put("name", file.displayName)
            .put("parents", JSONArray().put(folderId))
            .toString()

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setChunkedStreamingMode(0)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
            connectTimeout = 30_000
            readTimeout = 120_000
        }

        connection.outputStream.use { out ->
            out.writeAscii("--$BOUNDARY\r\n")
            out.writeAscii("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            out.write(metadata.toByteArray(Charsets.UTF_8))
            out.writeAscii("\r\n--$BOUNDARY\r\n")
            out.writeAscii("Content-Type: ${file.mimeType.ifBlank { "application/octet-stream" }}\r\n\r\n")

            val input = context.contentResolver.openInputStream(Uri.parse(file.contentUri))
                ?: throw IllegalStateException("File unreadable: ${file.displayName}")
            input.use { it.copyTo(out) }

            out.writeAscii("\r\n--$BOUNDARY--\r\n")
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            connection.disconnect()
            throw HttpStatusException(code, error ?: "HTTP $code")
        }
        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return JSONObject(responseText).getString("id")
    }

    private fun httpGet(urlString: String, token: String): String =
        httpRequest(urlString, token, method = "GET", body = null)

    private fun httpPostJson(urlString: String, token: String, body: String): String =
        httpRequest(urlString, token, method = "POST", body = body)

    private fun httpRequest(urlString: String, token: String, method: String, body: String?): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 30_000
            readTimeout = 60_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            connection.disconnect()
            throw HttpStatusException(code, error ?: "HTTP $code")
        }
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return text
    }

    private fun OutputStream.writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

    private class HttpStatusException(val code: Int, message: String) : Exception(message)

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val PHOTOS_APPEND_SCOPE = PhotosUploader.PHOTOS_APPEND_SCOPE
        const val FOLDER_NAME = "SwipeDelete Zero Backup"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val BOUNDARY = "sdz-backup-boundary-7f2a"
    }
}
