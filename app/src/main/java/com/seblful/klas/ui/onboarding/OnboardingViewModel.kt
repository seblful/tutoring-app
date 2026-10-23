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
 * Drives first-run setup: the tutor confirms the language, currency and lesson length, adds at least one
 * subject, then their first student (with subjects and prices). Completing it flips `AppSettings.onboardingComplete`,
 * after which the app shows its normal tabs.
 */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class OnboardingViewModel @Inject constructor(
    private val subjectsRepo: SubjectRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    private val firstLesson: FirstLesson,
) : ViewModel() {

    /** Current language and currency choices. */
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

    /** Stores how long a lesson usually is: what a booking starts at, and what the student step prices per. */
    fun setLessonMinutes(minutes: Int) = store { it.copy(defaultLessonMinutes = minutes) }

    /** Subjects added during setup. */
    val subjects: StateFlow<Loadable<List<Subject>>> = subjectsRepo.observeAll()
        .ready()
        .hold(this, Loadable.Loading)

    /**
     * Whether restoring a backup here would destroy something, and so has to be confirmed.
     *
     * Restoring replaces the database, so confirm when subjects have already been added during setup.
     */
    // Until subjects load, use the same first-run behavior as an empty list.
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

    /** Removes a subject added during setup. */
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

    /**
     * Ends first run — from finishing the last step or from skipping it — and, given [bookFor], has the app open
     * the booking form for that student when it comes up.
     */
    fun complete(bookFor: Long? = null) {
        bookFor?.let(firstLesson::bookFor)
        store { it.copy(onboardingComplete = true) }
    }

    private fun store(transform: (AppSettings) -> AppSettings) =
        appScope.write(events, Event.WRITE_FAILED) { settings.update(transform) }
}
