package com.seblful.tutoringapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.SettingsRepository
import com.seblful.tutoringapp.data.repository.SubjectRepository
import com.seblful.tutoringapp.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives first-run setup: the user adds at least one subject, then their first student (with
 * subjects and prices). Completing it flips [SettingsRepository.setOnboardingComplete], after which
 * the app shows its normal tabs.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val subjectsRepo: SubjectRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val subjects: StateFlow<List<Subject>> = subjectsRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSubject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { subjectsRepo.upsert(Subject(name = trimmed)) }
    }

    fun complete() {
        viewModelScope.launch { settings.setOnboardingComplete(true) }
    }
}
