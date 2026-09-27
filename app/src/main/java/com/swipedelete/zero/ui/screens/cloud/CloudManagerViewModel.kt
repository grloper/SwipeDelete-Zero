package com.swipedelete.zero.ui.screens.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.BackupReceiptEntity
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.CloudUploadStats
import com.swipedelete.zero.domain.backup.ConnectionCheck
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.backup.RemoteOriginal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudManagerUiState(
    val backupState: BackupState = BackupState.Unsupported,
    val uploadStats: CloudUploadStats = CloudUploadStats(),
    val uploads: List<CloudUploadEntity> = emptyList(),
    val backedUpFiles: List<BackedUpFileEntity> = emptyList(),
    val verifiedReceipts: List<BackupReceiptEntity> = emptyList(),
    val remoteOriginals: List<RemoteOriginal> = emptyList(),
    val isLoadingOriginals: Boolean = false,
    val selectedTab: Int = 0,
    val searchQuery: String = "",
    val connectionCheck: ConnectionCheck? = null,
    val isCheckingConnection: Boolean = false,
    val userMessage: String? = null,
) {
    val filteredBackedUpFiles: List<BackedUpFileEntity>
        get() = if (searchQuery.isBlank()) {
            backedUpFiles
        } else {
            backedUpFiles.filter { it.contentUri.contains(searchQuery, ignoreCase = true) || it.remoteId.contains(searchQuery, ignoreCase = true) }
        }

    val isCloudSupported: Boolean
        get() = backupState !is BackupState.Unsupported
}

@HiltViewModel
class CloudManagerViewModel @Inject constructor(
    private val cloudBackup: CloudBackup,
    private val photosArchive: PhotosArchive,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(0)
    private val searchQuery = MutableStateFlow("")
    private val connectionCheck = MutableStateFlow<ConnectionCheck?>(null)
    private val isCheckingConnection = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<String?>(null)
    private val remoteOriginals = MutableStateFlow<List<RemoteOriginal>>(emptyList())
    private val isLoadingOriginals = MutableStateFlow(false)

    val uiState: StateFlow<CloudManagerUiState> = combine(
        cloudBackup.state,
        photosArchive.uploadStats,
        backupRepository.observeCloudUploads(),
        backupRepository.observeBackedUpFiles(),
        backupRepository.observeVerifiedReceipts(),
        selectedTab,
        searchQuery,
        connectionCheck,
        isCheckingConnection,
        userMessage,
        remoteOriginals,
        isLoadingOriginals,
    ) { params ->
        val backupState = params[0] as BackupState
        val stats = params[1] as CloudUploadStats
        @Suppress("UNCHECKED_CAST")
        val uploads = params[2] as List<CloudUploadEntity>
        @Suppress("UNCHECKED_CAST")
        val backedUp = params[3] as List<BackedUpFileEntity>
        @Suppress("UNCHECKED_CAST")
        val receipts = params[4] as List<BackupReceiptEntity>
        val tab = params[5] as Int
        val query = params[6] as String
        val check = params[7] as? ConnectionCheck
        val isChecking = params[8] as Boolean
        val msg = params[9] as? String
        @Suppress("UNCHECKED_CAST")
        val originals = params[10] as List<RemoteOriginal>
        val loadingOriginals = params[11] as Boolean

        CloudManagerUiState(
            backupState = backupState,
            uploadStats = stats,
            uploads = uploads,
            backedUpFiles = backedUp,
            verifiedReceipts = receipts,
            remoteOriginals = originals,
            isLoadingOriginals = loadingOriginals,
            selectedTab = tab,
            searchQuery = query,
            connectionCheck = check,
            isCheckingConnection = isChecking,
            userMessage = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CloudManagerUiState(),
    )

    fun selectTab(tabIndex: Int) {
        selectedTab.value = tabIndex
        if (tabIndex == 2) refreshOriginals()
    }

    fun refreshOriginals() {
        viewModelScope.launch {
            isLoadingOriginals.value = true
            try {
                remoteOriginals.value = cloudBackup.availableOriginals()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                remoteOriginals.value = emptyList()
                userMessage.value = "Could not load Drive originals: ${e.message ?: "connection error"}"
            } finally {
                isLoadingOriginals.value = false
            }
        }
    }

    fun restore(original: RemoteOriginal, destination: Uri) {
        viewModelScope.launch {
            try {
                val success = cloudBackup.restoreOriginal(original, destination)
                userMessage.value = if (success) "Restored and checked ${original.name}"
                    else "Restore could not be verified. The backup is unchanged; discard any incomplete saved copy."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessage.value = "Restore failed: ${e.message ?: "connection error"}"
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun dismissUserMessage() {
        userMessage.value = null
    }

    fun retryUpload(uri: String) {
        photosArchive.retry(uri)
    }

    fun retryAllFailed() {
        photosArchive.retryAllFailed()
    }

    fun cancelUpload(uri: String) {
        viewModelScope.launch {
            photosArchive.cancel(uri)
        }
    }

    fun clearCompleted() {
        photosArchive.clearFinished()
    }

    fun forgetBackedUp(uri: String) {
        viewModelScope.launch {
            val removed = backupRepository.forgetBackedUp(uri)
            userMessage.value = if (removed) "Removed from cloud backup ledger" else "Item not found in ledger"
        }
    }

    fun rebackupFile(uri: String, displayName: String, mimeType: String, sizeBytes: Long) {
        viewModelScope.launch {
            backupRepository.rebackup(uri, displayName, mimeType, sizeBytes)
            photosArchive.retryAllFailed() // kicks worker if needed
            userMessage.value = "Re-backup queued for $displayName"
        }
    }

    fun verifyConnection() {
        viewModelScope.launch {
            isCheckingConnection.value = true
            try {
                connectionCheck.value = cloudBackup.verifyConnection()
            } catch (e: Exception) {
                userMessage.value = "Connection check failed: ${e.message}"
            } finally {
                isCheckingConnection.value = false
            }
        }
    }

    fun signInIntent(): Intent? = cloudBackup.signInIntent()

    fun onSignInResult(data: Intent?) {
        cloudBackup.onSignInResult(data)
        if (cloudBackup.state.value is BackupState.Ready) refreshOriginals()
    }

    fun backupNow() {
        cloudBackup.backupNow()
    }

    fun openInGooglePhotos(context: Context) {
        val intent = photosArchive.openInPhotosIntent()
        if (intent != null) {
            runCatching { context.startActivity(intent) }.onFailure {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://photos.google.com/"))) }
                    .onFailure { userMessage.value = "No app can open Google Photos." }
            }
        }
    }

    fun openBackedUpFile(context: Context, file: BackedUpFileEntity) {
        viewModelScope.launch {
            // Legacy Drive receipts use the raw file ID; Photos receipts are namespaced.
            val isPhotos = file.remoteId.startsWith("photos:")
            val url = if (isPhotos) photosArchive.remoteUrl(file.remoteId) else {
                file.remoteId.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
                    ?.let { "https://drive.google.com/file/d/$it/view" }
            }
            if (url == null) {
                userMessage.value = "Could not open this backup. Local deletion remains locked."
                return@launch
            }
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure { userMessage.value = "No app can open the backup link." }
        }
    }
}
