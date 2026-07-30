package com.seblful.klas.notifications

import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass

/**
 * Everything the app posts to the notification tray. A port, so what the app *decides* to announce can be
 * asserted without a device: posting is the only part that needs Android, and it sits behind this.
 *
 * Best-effort, like the calendar mirror: an adapter with no notification permission no-ops rather than
 * throwing, so a missing permission never fails a worker.
 *
 * **Four kinds, four channels** (see [ensureChannels]). They differ in what being late would cost, and
 * the app's scheduling follows that rather than the other way round: the reminder is an alarm because an
 * hour's warning delivered during the lesson is wrong rather than late, and the other three are ordinary
 * deferrable work (`domain/Digests`, `work/`).
 *
 * Amounts are printed in a [Currency] passed **in**, not read here. Currency is a display setting, and an
 * adapter that reached into `SettingsRepository` for it would have `notifications/` depending on
 * `data/repository/` for a label — the UI has the same rule the other way round (`Money.text` reads
 * `LocalCurrency` rather than the row).
 */
interface Notifier {

    /**
     * Creates every channel the app posts on, if they do not exist. Safe to call repeatedly.
     *
     * Called at **application start**, not lazily before the first post of each kind, and that matters
     * more than it looks: a channel the app has never created does not appear in system settings, so a
     * tutor cannot pre-mute a digest or turn the reminder's vibration off until the app has posted one at
     * least once. Creating them all up front makes every one of them tunable from the moment the app is
     * installed.
     */
    fun ensureChannels()

    /**
     * Posts a reminder for [klass].
     *
     * @param whoName who the lesson is with — the student, or the **group** when it is a group lesson,
     *   since one lesson earns one reminder and is announced by whoever is in the room.
     */
    fun showClassReminder(klass: TutoringClass, whoName: String, subjectName: String)

    /**
     * Takes the reminder for [classId] out of the tray.
     *
     * On the port rather than left to the adapter that posts, because it is a *consequence* the app
     * decides: a reminder answered by its own «Проведено» button must not stay sitting there, and
     * whether it was is what [ReminderActions] is tested on. Harmless for a reminder that is not showing.
     */
    fun cancelClassReminder(classId: Long)

    /**
     * Posts the morning digest — what today holds, and what it comes to.
     *
     * Takes the assembled [Digests.Agenda] rather than a day to go and read, for the reason every port
     * here takes data: which lessons a digest names, whose names those are and whether it is worth posting
     * at all are decisions, and decisions live where a JVM test can reach them ([DueDigest]).
     */
    fun showAgenda(agenda: Digests.Agenda, currency: Currency)

    /** Posts the weekly summary of who owes money. */
    fun showDebts(debts: Digests.Debts, currency: Currency)

    /**
     * Says that an unattended upkeep pass completed — and therefore **billed** — [lessons] lessons.
     *
     * The one notification the app posts about something it did on its own initiative, which is exactly
     * why it exists: auto-completion recognises income, and a background pass had been doing that in
     * total silence. A pass the tutor asked for reports through the screen they are standing in front of
     * (`SettingsViewModel.swept`); this is the same courtesy for the pass nobody was watching.
     *
     * @param lessons how many **lessons** were settled, not classes billed: a group's hour bills every
     *   member and is one lesson of the tutor's.
     * @param billed what those lessons came to, so the number that moved in the reports is on screen
     *   beside the count that moved it.
     */
    fun showAutoCompleted(lessons: Int, billed: Money, currency: Currency)
}
