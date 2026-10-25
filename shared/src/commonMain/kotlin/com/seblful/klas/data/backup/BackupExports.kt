package com.seblful.klas.data.backup

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import dev.zacsweers.metro.Inject
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A backup is complete after its file and history are saved, using the snapshot's one timestamp. */
class BackupExports @Inject constructor(
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val documents: DocumentStore,
    private val cipher: BackupCipher,
    private val folders: BackupFolder,
    private val clock: WallClock,
) {
    suspend fun toDocument(ref: DocumentRef, password: String? = null): Instant =
        write(password = password) { _, text -> documents.write(ref, text) }

    /** Weekly copies stay unsealed; a replaced folder receives neither history nor failure flags. */
    suspend fun toFolder(folder: String): Instant {
        val zone = clock.zone
        return runCatchingCancellable {
            write(folder = folder) { at, text ->
                folders.write(folder, AutoBackup.fileName(at.toLocalDateTime(zone).date.toString()), text)
            }
        }.getOrElse { failure ->
            settings.update { if (it.autoBackupFolder == folder) it.copy(autoBackupFailed = true) else it }
            throw failure
        }
    }

    private suspend fun write(
        folder: String? = null,
        password: String? = null,
        save: suspend (Instant, String) -> Unit,
    ): Instant {
        val at = clock.now()
        val json = backup.exportJson(at)
        save(at, password?.let { cipher.seal(json, it) } ?: json)
        settings.update {
            if (folder != null && it.autoBackupFolder != folder) it
            else it.copy(
                // A slower old snapshot must not replace the history of a newer successful export.
                lastExportAt = maxOf(at, it.lastExportAt ?: at),
                autoBackupFailed = if (folder != null) false else it.autoBackupFailed,
            )
        }
        return at
    }
}
