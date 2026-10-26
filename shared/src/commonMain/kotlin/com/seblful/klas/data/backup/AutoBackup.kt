package com.seblful.klas.data.backup

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.Inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A folder the tutor picked for weekly copies — on Android, a SAF tree they granted access to. */
interface BackupFolder {
    /** Writes [text] as a new file called [name] in [folder]. Throws if the folder cannot be reached. */
    suspend fun write(folder: String, name: String, text: String)

    /** The names of the files directly in [folder]. Throws if the folder cannot be reached. */
    suspend fun list(folder: String): List<String>

    suspend fun delete(folder: String, name: String)
}

/**
 * The weekly copy: once a week has passed since the last backup of any kind, a new file goes to the folder
 * in [com.seblful.klas.data.settings.AppSettings.autoBackupFolder], and only the newest [KEEP] of its own
 * copies stay. A manual export counts as the week's copy. A folder that cannot be written is flagged for
 * the Backup page instead of retried in silence — the tutor has to pick it again.
 */
@Inject
class AutoBackup(
    private val exports: BackupExports,
    private val settings: SettingsRepository,
    private val folders: BackupFolder,
    private val clock: WallClock,
) {
    enum class Outcome { OFF, NOT_DUE, SAVED, FAILED }

    suspend fun runIfDue(): Outcome {
        val current = settings.get()
        val folder = current.autoBackupFolder
        return when {
            folder == null -> Outcome.OFF
            !isDue(current.lastExportAt, clock.now()) -> Outcome.NOT_DUE
            else -> save(folder)
        }
    }

    private suspend fun save(folder: String): Outcome {
        val saved = runCatchingCancellable { exports.toFolder(folder) }
        if (saved.isFailure) return Outcome.FAILED
        // Pruning is tidying: a copy that could not be deleted does not make this one less saved.
        runCatchingCancellable { prune(folder) }
        return Outcome.SAVED
    }

    private suspend fun prune(folder: String) {
        folders.list(folder)
            .filter { OWN_FILE.matches(it) }
            .sortedDescending()
            .drop(KEEP)
            .forEach { folders.delete(folder, it) }
    }

    companion object {
        private const val PREFIX = "klas-auto-backup-"

        /** How many of its own copies the folder keeps. */
        const val KEEP = 5

        val EVERY = 7.days

        /** The check runs about daily; one landing a little early still counts the week as done. */
        private val SLACK = 12.hours

        /** Named apart from manual exports so pruning can never touch a file the tutor saved by hand. */
        fun fileName(day: String) = "$PREFIX$day.json"

        /** Ours: `klas-auto-backup-2026-10-23.json`, or the `… (1).json` a provider names a same-day second. */
        private val OWN_FILE = Regex("""${Regex.escape(PREFIX)}\d{4}-\d{2}-\d{2}( \(\d+\))?\.json""")

        fun isDue(lastExportAt: Instant?, now: Instant): Boolean =
            lastExportAt == null || lastExportAt > now || now - lastExportAt >= EVERY - SLACK

        /** Past this, the Backup page warns that recent work is on this phone alone. */
        val STALE_AFTER = 14.days

        fun isStale(lastExportAt: Instant?, now: Instant): Boolean =
            lastExportAt == null || lastExportAt > now || now - lastExportAt > STALE_AFTER
    }
}
