package com.seblful.klas.ui.backup

import com.seblful.klas.data.backup.BackupFailure
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts

/**
 * What to tell the tutor about [BackupFailure]. One mapping, shared by the backup screen and the
 * first-run restore, so the two places a backup can be imported cannot word the same refusal
 * differently.
 */
fun BackupFailure.messageIn(texts: Texts): String = when (this) {
    is BackupFailure.UnsupportedVersion ->
        texts.string(Strings.backup_error_version, found, supported)

    BackupFailure.FileUnavailable -> texts.string(Strings.backup_error_file)
    BackupFailure.Malformed -> texts.string(Strings.backup_error_malformed)
    BackupFailure.PasswordNeeded -> texts.string(Strings.backup_error_password_needed)
    BackupFailure.WrongPassword -> texts.string(Strings.backup_error_wrong_password)
}
