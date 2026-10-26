package com.seblful.klas.ui.backup

/** What an export does with the file the picker created. */
internal sealed interface PickedExport {
    data class Write(val password: String?) : PickedExport

    /** A password was asked for, and the process was put away before the picker answered. */
    data object PasswordLost : PickedExport
}

/**
 * Whether the file the picker answered with can be written: a password asked for and since lost must not
 * turn into an unsealed file the tutor believes is sealed.
 */
internal fun pickedExport(sealedRequested: Boolean, password: String?): PickedExport =
    if (sealedRequested && password == null) PickedExport.PasswordLost else PickedExport.Write(password)
