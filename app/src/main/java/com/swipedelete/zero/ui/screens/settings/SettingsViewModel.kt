package com.swipedelete.zero.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.ExclusionEntity
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.data.repository.ExclusionRepository
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exclusionRepository: ExclusionRepository,
    private val cloudBackup: CloudBackup,
    backupRepository: BackupRepository,
) : ViewModel() {

    val exclusions: StateFlow<List<ExclusionEntity>> =
        exclusionRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val backupState: StateFlow<BackupState> = cloudBackup.state

    val pendingBackupCount: StateFlow<Int> =
        backupRepository.observePendingBackupCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val backedUpCount: StateFlow<Int> =
        backupRepository.observeBackedUpCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun signInIntent(): Intent? = cloudBackup.signInIntent()
    fun onSignInResult(data: Intent?) = cloudBackup.onSignInResult(data)
    fun backupNow() = cloudBackup.backupNow()
    fun disconnectBackup() = cloudBackup.signOut()

    fun remove(id: Long) = viewModelScope.launch { exclusionRepository.remove(id) }
}
