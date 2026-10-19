package com.seblful.klas.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.common.state.writeAwaited
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives first-run setup: the tutor confirms the language and currency, adds at least one subject, then
 * their first student (with subjects and prices). Completing it flips `AppSettings.onboardingComplete`,
 * after which the app shows its normal tabs.
 *
 * Setup is offered, not imposed — [complete] is also what «Пропустить» calls, leaving an app with
 * no subjects and no students, which every screen already handles with its empty state. Restoring
 * a backup ends first run too, but that path belongs to `BackupRestore` (it does far more than flip
 * the flag), not here.
 *
 * Every write goes to [appScope] through the shared guarded tail (`ui/common/state/Writes.kt`), like every
 * other screen's: they used to be bare `viewModelScope.launch`es, so a database error closed the app in
 * the middle of its first minute, and «Готово» followed by a quick back could cancel the flag's write.
 */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class OnboardingViewModel @Inject constructor(
    private val subjectsRepo: SubjectRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /**
     * The language and the currency the app is about to be read in — step 1's two answers.
     *
     * **Both arrive already correct for this phone**, which is the whole of what the step is for: the
     * language defaults to `AppLanguage.SYSTEM` and the currency is substituted from the device by
     * `SettingsRepository` for a row that does not exist yet, so a tutor whose phone already says what they
     * want passes straight through. The step exists because the *guess* has to be checkable — and because a
     * Minsk tutor with an English phone is the case the guess gets half right.
     *
     * [Loadable] rather than defaulted, for the reason every settings screen's row is: `AppSettings()` is
     * somebody else's answer, and drawing BYN under a segmented control that then jumps to USD reads as the
     * app changing its mind rather than as the app catching up.
     */
    val locale: StateFlow<Loadable<AppSettings>> = settings.observe()
        .ready()
        .hold(this, Loadable.Loading)

    /**
     * Stores the language, which **applies as it is tapped** — `AppRoot` provides the whole composition's
     * locale from this row, so the next frame of this very screen is in the new one.
     *
     * That is not a nicety: it is what makes the pre-set answer above something the tutor can check rather
     * than something they have to believe. There is no confirmation, unlike the currency in Settings later
     * on — a language changes the words around the numbers, where a currency changes what the numbers claim
     * to be, and at this point in the setup there are no numbers to re-label.
     *
     * Stored without the tail `SettingsViewModel.update` discharges, because during first run it is always
     * empty: a language change owes the calendar a re-mirror only while sync is on, sync is off until the
     * settings screen — which first run never reaches — turns it on, and a language moves no reminder or
     * digest.
     */
    fun setLanguage(language: AppLanguage) = store { it.copy(language = language) }

    /** Stores the currency. Asks nothing: nothing has been recorded yet for a re-label to touch. */
    fun setCurrency(currency: Currency) = store { it.copy(currency = currency) }

    /**
     * The subjects added so far, for step 2's list.
     *
     * [Loadable] like every other screen's list, though this is the one screen where empty is usually the
     * *true* answer: onboarding runs on a fresh install. It is not always — the step is reachable again
     * after subjects have been added, and a tutor who has just typed three of them must not be shown
     * «Нет предметов» over them, with «Далее» disabled, while the list is being re-read.
     */
    val subjects: StateFlow<Loadable<List<Subject>>> = subjectsRepo.observeAll()
        .ready()
        .hold(this, Loadable.Loading)

    /**
     * Whether restoring a backup here would destroy something, and so has to be confirmed.
     *
     * On a fresh install there is nothing to replace, and a confirmation between the file picker and the
     * import would be a question with one sensible answer. But the tutor can add subjects in step 1 and
     * come back — an import is a **full replace**, so those subjects would go without being mentioned.
     * The gate was an `if (subjects.isEmpty())` in the picker's result callback, which is both a rule
     * about the backup format and a place no test reaches.
     */
    // A list not yet read is nothing to destroy: the question is asked only about subjects known to be
    // there, and an unread list makes the restore the unguarded one it is on a fresh install.
    fun restoreNeedsConfirming(): Boolean = subjects.value.valueOrNull().orEmpty().isNotEmpty()

    /**
     * Adds a subject — unless one of that name is already there, which is refused as [Event.NAME_TAKEN]
     * the way the subjects screen refuses it ([SubjectRepository.saveUnlessTaken]). A double tap on
     * «Добавить» was enough to make two «Математика».
     *
     * Checked against the table inside the write rather than against [subjects], so a list not yet read
     * cannot let the second one through.
     */
    fun addSubject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        appScope.write(events, Event.WRITE_FAILED, onWritten = { added -> if (!added) events.send(Event.NAME_TAKEN) }) {
            subjectsRepo.saveUnlessTaken(Subject(name = trimmed))
        }
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
            appScope.writeAwaited { subjectsRepo.delete(subject) }
                .onSuccess { if (it == SubjectRepository.Delete.Ok) onRemoved() else events.send(Event.REMOVE_REFUSED) }
                .onFailure { events.send(Event.WRITE_FAILED) }
        }
    }

    /** One-shot signals from an action that did not go through. */
    enum class Event {
        REMOVE_REFUSED,

        /** A subject of that name is already there — see [addSubject]. */
        NAME_TAKEN,

        /** A write failed — a database error rather than anything the tutor did. */
        WRITE_FAILED,
    }

    val events = UiEvents<Event>()

    /** Ends first run — from finishing the last step or from skipping it. */
    fun complete() = store { it.copy(onboardingComplete = true) }

    private fun store(transform: (AppSettings) -> AppSettings) =
        appScope.write(events, Event.WRITE_FAILED) { settings.update(transform) }
}
