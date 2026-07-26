package com.seblful.klas.data.backup

import kotlinx.serialization.SerializationException

/**
 * Thrown when a file is a backup this build cannot read. Carries the two numbers rather than only a
 * message, so the screen can *tell the tutor* which version they have and which one this app reads —
 * the whole reason [BackupData.version] exists is to refuse a newer file, and a refusal that doesn't
 * say so leaves them with a file the app won't open and no idea why.
 */
class UnsupportedBackupVersionException(
    val found: Int,
    val supported: Int,
) : IllegalArgumentException("Unsupported backup version $found (this build supports up to $supported)")

/**
 * Why an export or an import could not go through — the distinctions that change what the tutor
 * should *do* about it.
 *
 * This used to be one boolean: every failure became `ERROR` and one message, «Не удалось — проверьте
 * файл», which is advice that only fits one of the three cases. A file from a newer build is not a
 * file to check, it is an app to update; a file that is not a backup at all is not a permission
 * problem. [BackupManager] already knew which had happened and the information was discarded on the
 * way out.
 *
 * Classification is pure and lives in [of], so which exception means what is decided in one testable
 * place rather than inside a view model's `catch`.
 */
sealed interface BackupFailure {

    /** The file was written by a newer build. The remedy is to update the app, not to pick again. */
    data class UnsupportedVersion(val found: Int, val supported: Int) : BackupFailure

    /**
     * The file could not be read or written at all — no longer there, permission withdrawn, storage
     * gone. Nothing is known about its contents, because they were never reached.
     */
    data object FileUnavailable : BackupFailure

    /** The file was read, and is not a Klas backup: a different JSON, or a truncated one. */
    data object Malformed : BackupFailure

    companion object {
        /**
         * Which failure [error] is. A [SerializationException] means the bytes arrived and did not
         * parse; anything else that isn't a version refusal means they never arrived, since by then
         * the only remaining step is the document store's own I/O.
         */
        fun of(error: Throwable): BackupFailure = when (error) {
            is UnsupportedBackupVersionException -> UnsupportedVersion(error.found, error.supported)
            is SerializationException -> Malformed
            else -> FileUnavailable
        }
    }
}
