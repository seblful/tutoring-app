package com.seblful.klas.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.UiEvents
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

    /**
     * Removes a subject added in step 1 — the way out of a typo.
     *
     * The step listed what had been added and offered nothing to do about it, so «Матеamtика» could only
     * be lived with: the setup's one gate is "at least one subject", so the tutor either finished with a
     * misspelled subject on every future lesson, or skipped setup to reach the screen that can delete
     * one.
     *
     * **The refusal cannot arrive here, and is reported rather than dropped.** `delete` refuses a subject
     * that has lessons or a group ([SubjectRepository.Delete]); during first run neither exists — the
     * only student form is step 2, and saving it is the same tap that ends onboarding — so a subject
     * added here is referenced by nothing. That is an argument about today's flow rather than about the
     * repository, which is why the outcome is still checked: if the setup ever grows a step that
     * references a subject, this says so instead of appearing to do nothing.
     *
     * [onRemoved] runs **only if the row actually went**, which is what keeps the screen's undo honest.
     * The obvious shape — show «Предмет удалён» beside the call and let the refusal arrive as an event —
     * says two contradictory things in a row when the delete is refused, and worse, offers a «Вернуть»
     * that would *add a second subject of the same name*: `subjects` has no unique index on its name, so
     * nothing downstream would refuse it. Reporting through the caller rather than through [events] for
     * the same reason `save(onDone)` and `delete(onDeleted)` do elsewhere in this app: the screen has
     * something to do *after* the write, not merely something to say about it.
     */
    fun removeSubject(subject: Subject, onRemoved: () -> Unit) {
        viewModelScope.launch {
            if (subjectsRepo.delete(subject) == SubjectRepository.Delete.Ok) {
                onRemoved()
            } else {
                events.send(Event.REMOVE_REFUSED)
            }
        }
    }

    /** One-shot signals from an action that did not go through. */
    enum class Event { REMOVE_REFUSED }

    val events = UiEvents<Event>()

    /** Ends first run — from finishing the last step or from skipping it. */
    fun complete() {
        viewModelScope.launch { settings.update { it.copy(onboardingComplete = true) } }
    }
}
