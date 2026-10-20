package com.seblful.klas.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsRepository
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
 */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class OnboardingViewModel @Inject constructor(
    private val subjectsRepo: SubjectRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** he language and the currency the app is about to be read in — step 1's two answers. */
    val locale: StateFlow<Loadable<AppSettings>> = settings.observe()
        .ready()
        .hold(this, Loadable.Loading)

    /**
     * Stores the language, which **applies as it is tapped** — `AppRoot` provides the whole composition's
     * locale from this row, so the next frame of this very screen is in the new one.
     */
    fun setLanguage(language: AppLanguage) = store { it.copy(language = language) }

    /** Stores the currency. Asks nothing: nothing has been recorded yet for a re-label to touch. */
    fun setCurrency(currency: Currency) = store { it.copy(currency = currency) }

    /** he subjects added so far, for step 2's list. */
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
     */
    fun addSubject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        appScope.write(events, Event.WRITE_FAILED, onWritten = { added -> if (!added) events.send(Event.NAME_TAKEN) }) {
            subjectsRepo.saveUnlessTaken(Subject(name = trimmed))
        }
    }

    /** emoves a subject added in step 1 — the way out of a typo. */
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
