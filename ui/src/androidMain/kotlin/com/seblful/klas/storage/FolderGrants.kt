package com.seblful.klas.storage

/** Keeping and giving up access to a picked folder, and naming it for the screen. */
interface FolderGrants {
    /** Keeps access to [folder] across restarts. Throws if the picker granted none to keep. */
    fun keep(folder: String)

    fun release(folder: String)

    /** The folder's own name, or null when the provider no longer answers for it. */
    suspend fun name(folder: String): String?
}
