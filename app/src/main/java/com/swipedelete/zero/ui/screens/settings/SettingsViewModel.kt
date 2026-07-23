package com.swipedelete.zero.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.ExclusionEntity
import com.swipedelete.zero.data.repository.ExclusionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exclusionRepository: ExclusionRepository,
) : ViewModel() {

    val exclusions: StateFlow<List<ExclusionEntity>> =
        exclusionRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: Long) = viewModelScope.launch { exclusionRepository.remove(id) }
}
