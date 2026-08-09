package com.swipedelete.zero.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.ConnectionCheck
import com.swipedelete.zero.domain.setup.AuthDiagnostic
import com.swipedelete.zero.domain.setup.CloudSetupPlan
import com.swipedelete.zero.domain.setup.SetupStep
import com.swipedelete.zero.domain.setup.SigningIdentity
import com.swipedelete.zero.domain.setup.SigningIdentityReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudSetupUiState(
    val identity: SigningIdentity,
    val backupState: BackupState = BackupState.SignedOut(),
    /** Which step card is expanded. */
    val expandedStep: SetupStep = SetupStep.CREATE_PROJECT,
    /** Steps the user has ticked off manually, plus SIGN_IN once connected. */
    val completedSteps: Set<SetupStep> = emptySet(),
    val verifying: Boolean = false,
    val lastCheck: ConnectionCheck? = null,
) {
    val isConnected: Boolean get() = backupState is BackupState.Ready

    /** Decoded failure from the last sign-in attempt, if any. */
    val diagnostic: AuthDiagnostic?
        get() = (backupState as? BackupState.SignedOut)?.diagnostic
            ?: lastCheck?.diagnostic

    val steps = CloudSetupPlan.steps

    fun isComplete(step: SetupStep): Boolean =
        step in completedSteps || (step == SetupStep.SIGN_IN && isConnected)
}

@HiltViewModel
class CloudSetupViewModel @Inject constructor(
    private val cloudBackup: CloudBackup,
    signingIdentityReader: SigningIdentityReader,
) : ViewModel() {

    private val identity = signingIdentityReader.read()

    private val expandedStep = MutableStateFlow(SetupStep.CREATE_PROJECT)
    private val completedSteps = MutableStateFlow(emptySet<SetupStep>())
    private val verifying = MutableStateFlow(false)
    private val lastCheck = MutableStateFlow<ConnectionCheck?>(null)

    val uiState: StateFlow<CloudSetupUiState> = combine(
        cloudBackup.state,
        expandedStep,
        completedSteps,
        verifying,
        lastCheck,
    ) { backup, expanded, completed, isVerifying, check ->
        CloudSetupUiState(
            identity = identity,
            backupState = backup,
            expandedStep = expanded,
            completedSteps = completed,
            verifying = isVerifying,
            lastCheck = check,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CloudSetupUiState(identity = identity),
    )

    init {
        // A failure always reveals the step that can fix it, so the user is never
        // left hunting for where to go next.
        viewModelScope.launch {
            cloudBackup.state.collect { state ->
                (state as? BackupState.SignedOut)?.diagnostic?.blamedStep?.let {
                    expandedStep.value = it
                }
            }
        }
    }

    fun expand(step: SetupStep) {
        expandedStep.value = step
    }

    fun toggleComplete(step: SetupStep) {
        completedSteps.value = completedSteps.value.let {
            if (step in it) it - step else it + step
        }
        // Ticking a step advances the wizard to the next unfinished one.
        if (step in completedSteps.value) {
            CloudSetupPlan.steps
                .map { it.step }
                .firstOrNull { it !in completedSteps.value }
                ?.let { expandedStep.value = it }
        }
    }

    fun signInIntent() = cloudBackup.signInIntent()

    fun onSignInResult(data: android.content.Intent?) {
        cloudBackup.onSignInResult(data)
        lastCheck.value = null
    }

    fun signOut() {
        cloudBackup.signOut()
        lastCheck.value = null
    }

    /** Prove the setup works by actually calling the APIs. */
    fun verify() {
        if (verifying.value) return
        viewModelScope.launch {
            verifying.value = true
            lastCheck.value = runCatching { cloudBackup.verifyConnection() }
                .getOrElse {
                    ConnectionCheck(
                        signedIn = false,
                        message = "Verification failed: ${it.message ?: it.javaClass.simpleName}",
                    )
                }
            verifying.value = false
        }
    }
}
