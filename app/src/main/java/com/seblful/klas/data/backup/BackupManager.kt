package com.seblful.klas.data.backup

import com.seblful.klas.data.local.SnapshotDao
import com.seblful.klas.data.local.Transactor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports the whole database to a JSON string and restores it. Import is a full replace inside
 * a single transaction, so a failure leaves the existing data untouched (no partial restore).
 *
 * What it knows is the **file format** — the version, the JSON, and which of them this build will
 * accept. What tables there are, and the order they may be emptied and filled in, is the schema's and
 * lives with it ([SnapshotDao]). This used to hold seven DAOs and both foreign-key orderings itself,
 * which put a wipe of every table in reach of every repository that shared those DAOs.
 */
@Singleton
class BackupManager @Inject constructor(
    private val transactor: Transactor,
    private val snapshot: SnapshotDao,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(): BackupData = snapshot.read().toBackupData()

    suspend fun exportJson(): String = json.encodeToString(export())

    fun parse(text: String): BackupData = json.decodeFromString(text)

    suspend fun import(data: BackupData) {
        if (!BackupData.isSupported(data.version)) {
            throw UnsupportedBackupVersionException(
                found = data.version,
                supported = BackupData.CURRENT_VERSION,
            )
        }
        // Both refusals happen before anything is deleted. The version says whether this build
        // understands the *format*; the integrity check says whether it can read the *rows* — which
        // matters because the rows go in verbatim and are only interpreted later, on the read path.
        // See [BackupIntegrity].
        BackupIntegrity.check(data)
        // The transaction is here rather than on the DAO so it is the injected port, and so a JVM test
        // can count it: `replace` empties every table before it fills any, and a restore that stopped
        // in between would have destroyed the tutor's data to import nothing.
        transactor.transaction { snapshot.replace(data.toSnapshot()) }
    }

    suspend fun importJson(text: String) = import(parse(text))
}
