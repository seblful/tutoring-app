package com.seblful.klas.ui.backup

import android.content.Context
import com.seblful.klas.R
import com.seblful.klas.data.backup.BackupFailure

/**
 * What to tell the tutor about [BackupFailure]. One mapping, shared by the backup screen and the
 * first-run restore, so the two places a backup can be imported cannot word the same refusal
 * differently.
 */
fun BackupFailure.messageIn(context: Context): String = when (this) {
    is BackupFailure.UnsupportedVersion ->
        context.getString(R.string.backup_error_version, found, supported)

    BackupFailure.FileUnavailable -> context.getString(R.string.backup_error_file)
    BackupFailure.Malformed -> context.getString(R.string.backup_error_malformed)
}
