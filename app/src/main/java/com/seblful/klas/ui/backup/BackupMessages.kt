package com.seblful.klas.ui.backup

import android.content.Context
import com.seblful.klas.R
import com.seblful.klas.data.backup.BackupFailure

/**
 * What to tell the tutor about [BackupFailure]. One mapping, shared by the backup screen and the
 * first-run restore, so the two places a backup can be imported cannot word the same refusal
 * differently.
 *
 * Takes a [Context] rather than being `@Composable`: both callers read it inside a
 * `LaunchedEffect` collecting the view model's events, where a `stringResource` cannot be, and the
 * version message needs its two numbers formatted in — so it can't be hoisted and resolved ahead of
 * time the way a fixed string can.
 */
fun BackupFailure.messageIn(context: Context): String = when (this) {
    is BackupFailure.UnsupportedVersion ->
        context.getString(R.string.backup_error_version, found, supported)

    BackupFailure.FileUnavailable -> context.getString(R.string.backup_error_file)
    BackupFailure.Malformed -> context.getString(R.string.backup_error_malformed)
}
