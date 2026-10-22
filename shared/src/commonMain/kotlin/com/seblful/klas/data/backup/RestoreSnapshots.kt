package com.seblful.klas.data.backup

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * Where what was on the phone before the last restore is kept, so that restore can be undone. One copy,
 * in app-private storage, replaced by the next restore — see [BackupRestore.undoRestore].
 */
interface RestoreSnapshots {
    /** When the copy kept was taken, or null when there is none. */
    val keptAt: StateFlow<Instant?>

    suspend fun save(json: String, takenAt: Instant)

    suspend fun read(): String?

    suspend fun clear()
}

/** What a backup file holds, said before it replaces anything. */
data class BackupPreview(
    /** When the file was made — null for one exported before files said so. */
    val exportedAt: Instant?,
    val students: Int,
    val groups: Int,
    val lessons: Int,
    val payments: Int,
    val homework: Int,
)
