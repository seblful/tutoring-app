package com.seblful.repetit.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives first-run setup: the user adds at least one subject, then their first student (with
 * subjects and prices). Completing it flips `AppSettings.onboardingComplete`, after which
 * the app shows its normal tabs.
 *
 * Setup is offered, not imposed — [complete] is also what «Пропустить» calls, leaving an app with
 * no subjects and no students, which every screen already handles with its empty state. Restoring
 * a backup ends first run too, but that path belongs to `BackupRestore` (it does far more than flip
 * the flag), not here.
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

    /** Ends first run — from finishing the last step or from skipping it. */
    fun complete() {
        viewModelScope.launch { settings.update { it.copy(onboardingComplete = true) } }
    }
}
