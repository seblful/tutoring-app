package com.seblful.tutoringapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Decides whether to show first-run onboarding or the main app. `null` means still loading. */
@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val onboardingComplete: StateFlow<Boolean?> = settings.observe()
        .map { it.onboardingComplete }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
