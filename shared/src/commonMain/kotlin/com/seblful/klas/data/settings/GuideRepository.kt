package com.seblful.klas.data.settings

import com.seblful.klas.domain.guide.FirstSteps
import com.seblful.klas.domain.guide.Guide
import com.seblful.klas.domain.guide.PhoneStep
import com.seblful.klas.domain.guide.Tip
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The guide a new tutor gets once setup is done: the first-steps card on Today and a tip for each gesture no
 * screen can show. Kept in the settings row, so it stays on this phone — a restored backup marks it done
 * (see `BackupRestore`) rather than carrying another phone's, and leaves instead what this phone is still to be
 * given ([phoneSetup]).
 */
@SingleIn(AppScope::class)
class GuideRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val dao: GuideDao,
) {
    val guide: Flow<Guide> = settings.observe().map { it.guide }.distinctUntilChanged()

    /** What has happened at least once, which ticks the first steps. */
    val progress: Flow<FirstSteps.Progress> = dao.observeProgress()
        .map { FirstSteps.Progress(booked = it.booked, taught = it.taught, paid = it.paid, homework = it.homework) }
        .distinctUntilChanged()

    /** What this phone is to be given after a restore; empty when there is no card. */
    val phoneSetup: Flow<Set<PhoneStep>> = settings.observe().map { it.phoneSetup }.distinctUntilChanged()

    suspend fun seen(tip: Tip) {
        settings.update { it.copy(guide = it.guide.seeing(tip)) }
    }

    /** Puts the first-steps card away, or — the hide's Undo — back. */
    suspend fun setFirstStepsHidden(hidden: Boolean) {
        settings.update { it.copy(guide = it.guide.copy(firstStepsHidden = hidden)) }
    }

    /** Puts the phone-setup card away (empty), or — the hide's Undo — back with [steps]. */
    suspend fun setPhoneSetup(steps: Set<PhoneStep>) {
        settings.update { it.copy(phoneSetup = steps) }
    }

    /** Settings' «Show tips again»: every tip, and the card, as on the day the app was set up. */
    suspend fun showAgain() {
        settings.update { it.copy(guide = Guide.FRESH) }
    }
}
