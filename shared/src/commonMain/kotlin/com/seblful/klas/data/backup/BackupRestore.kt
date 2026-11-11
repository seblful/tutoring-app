package com.seblful.klas.data.backup

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.guide.Guide
import com.seblful.klas.domain.guide.PhoneStep
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * Everything restoring a backup entails beyond replacing the rows: re-planning reminders,
 * re-mirroring the classes to *this* device's calendar (the imported event ids came from another
 * one), and marking first-run setup done — restored data *is* the setup, so a tutor who restores
 * on a fresh install lands in the app instead of being asked to add a subject and a student.
 *
 * Both entry points (Ещё → Резервная копия and the first-run screen) go through here, so neither
 * can forget a step. View models read through `DocumentStore` using the picker's `DocumentRef`.
 */
@SingleIn(AppScope::class)
class BackupRestore @Inject constructor(
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val calendar: CalendarMirror,
    private val reminders: ReminderScheduler,
    private val snapshots: RestoreSnapshots,
    private val clock: WallClock,
) {
    /** When the data from before the last restore was kept, while it can still be put back. */
    val undoableSince: StateFlow<Instant?> get() = snapshots.keptAt

    /**
     * Replaces all data with [json]. Throws if it isn't a supported backup — the import is one
     * transaction, so a rejected file leaves the current data (and the onboarding flag) untouched.
     *
     * What was here is kept first, so the restore can be undone ([undoRestore]) — unless there was
     * nothing, as on first run. It is kept only once the restore has gone through: a refused file leaves
     * the previous copy, if any, as it was.
     *
     * The data is then the file's, so the file is the last backup: its own date, or now for a file too old
     * to carry one — never earlier than a backup already made. The settings that give the data its meaning
     * come with it ([BackupPreferences]), and what the file's phone had on that this one has to be given by
     * hand is left for Today to ask for (`PhoneSetup`).
     */
    suspend fun restore(json: String) {
        val data = backup.parse(json)
        val before = backup.export()
        val keep = before.subjects.isNotEmpty() || before.students.isNotEmpty()
        val takenAt = clock.now()
        val copy = if (keep) backup.encode(before, takenAt) else null
        replaceWith(
            data,
            // A date after now is a clock that was wrong, and would hold the weekly copy off until it passed.
            backedUpAt = data.exportedAtMillis?.let { Instant.fromEpochMilliseconds(it) }
                ?.coerceAtMost(takenAt) ?: takenAt,
            phoneSetup = data.preferences.phoneSetup(),
            earlyUser = data.preferences.earlyUser(),
        )
        // The restore has happened whether or not its undo could be kept, so a failure here is not its.
        // The one still kept would put back older data than what was just replaced, so it goes.
        copy?.let {
            runCatchingCancellable { snapshots.save(it, takenAt) }
                .onFailure { runCatchingCancellable { snapshots.clear() } }
        }
    }

    /**
     * Puts back what was here before the last restore, and forgets it. False when nothing is kept. The last
     * backup stays as it is: the kept copy is the app's own, not a file the tutor has. So does what this phone is
     * to be given: it is the same phone either way.
     */
    suspend fun undoRestore(): Boolean {
        val kept = snapshots.read() ?: return false
        replaceWith(backup.parse(kept), backedUpAt = null, phoneSetup = null, earlyUser = false)
        snapshots.clear()
        return true
    }

    /**
     * @param phoneSetup what this phone is to be asked for; null leaves what it is already asked for.
     * @param earlyUser the data's tutor predates Klas Pro. Only ever grants: a phone that is early stays early.
     */
    private suspend fun replaceWith(
        data: BackupData,
        backedUpAt: Instant?,
        phoneSetup: Set<PhoneStep>?,
        earlyUser: Boolean,
    ) {
        calendar.remirrorAfter {
            backup.import(data)
            // Whoever restores already knows the app, so the new tutor's guide is not shown to them.
            settings.update { current ->
                val restored = data.preferences?.let { current.applying(it) } ?: current
                restored.copy(
                    onboardingComplete = true,
                    guide = Guide.DONE,
                    lastExportAt = listOfNotNull(current.lastExportAt, backedUpAt).maxOrNull(),
                    phoneSetup = phoneSetup ?: current.phoneSetup,
                    earlyUser = current.earlyUser || earlyUser,
                )
            }
        }
        // Imported classes change what's upcoming/overdue — re-run auto-complete & reminders. After the
        // re-mirror rather than inside it: the rows are already replaced, and a failure here must not
        // leave the old events standing beside the new ones.
        reminders.refreshNow()
    }
}
