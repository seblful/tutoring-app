package com.seblful.klas.storage

import android.content.Context
import com.seblful.klas.data.backup.RestoreSnapshots
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.di.Io
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Instant

/**
 * [RestoreSnapshots] as one file in app-private storage, so it is never in a backup or a shared folder.
 * The file's modification time is when the copy was taken.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class FileRestoreSnapshots @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Io private val io: CoroutineDispatcher,
) : RestoreSnapshots {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    private val _keptAt = MutableStateFlow(
        file.takeIf { it.exists() }?.let { Instant.fromEpochMilliseconds(it.lastModified()) },
    )
    override val keptAt: StateFlow<Instant?> = _keptAt.asStateFlow()

    override suspend fun save(json: String, takenAt: Instant) = withContext(io) {
        // Written beside and then moved, so a kill mid-write cannot leave half a copy to restore.
        val partial = File(context.filesDir, "$FILE_NAME.part")
        partial.writeText(json)
        partial.setLastModified(takenAt.toEpochMilliseconds())
        check(partial.renameTo(file)) { "Could not keep the data from before the restore" }
        _keptAt.value = takenAt
    }

    override suspend fun read(): String? = withContext(io) {
        file.takeIf { it.exists() }?.readText()
    }

    override suspend fun clear() = withContext(io) {
        file.delete()
        _keptAt.value = null
    }

    private companion object {
        const val FILE_NAME = "restore-undo.json"
    }
}
