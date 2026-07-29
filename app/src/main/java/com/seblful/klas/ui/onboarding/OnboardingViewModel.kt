package com.seblful.klas.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.derive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
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
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    val subjects: StateFlow<List<Subject>> = subjectsRepo.observeAll()
        .derive(this, computation, emptyList())

    /**
     * Whether restoring a backup here would destroy something, and so has to be confirmed.
     *
     * On a fresh install there is nothing to replace, and a confirmation between the file picker and the
     * import would be a question with one sensible answer. But the tutor can add subjects in step 1 and
     * come back — an import is a **full replace**, so those subjects would go without being mentioned.
     * The gate was an `if (subjects.isEmpty())` in the picker's result callback, which is both a rule
     * about the backup format and a place no test reaches.
     */
    fun restoreNeedsConfirming(): Boolean = subjects.value.isNotEmpty()

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
