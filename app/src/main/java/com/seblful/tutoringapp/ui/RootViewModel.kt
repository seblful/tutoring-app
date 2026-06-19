package com.seblful.tutoringapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.AppSettings
import com.seblful.tutoringapp.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Top-level app state: theme, currency, and whether onboarding is done. `null` means settings are
 * still loading (show a spinner under the default theme).
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
