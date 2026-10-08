package com.seblful.klas.data.backup

import com.seblful.klas.data.local.SnapshotDao
import com.seblful.klas.data.local.Transactor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
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

    /**
     * The file as a [BackupData] — with its **version read first**, and a newer one refused before any row
     * is decoded.
     *
     * The rows' enum columns are typed, so a name this build has no constant for fails the decode — which
     * is right for a file of this build's version or older (it is malformed: no build ever wrote that name),
     * and wrong for a newer one: a later build that adds a status writes it into its files, and those have
     * to be refused as *newer*, telling the tutor to update the app, rather than as a broken file. So the
     * version is read off the bare JSON before the rows are; a version that is missing or not a number is
     * left to the decode, which defaults the one and refuses the other.
     */
    fun parse(text: String): BackupData {
        val tree = json.parseToJsonElement(text)
        val version = ((tree as? JsonObject)?.get("version") as? JsonPrimitive)?.intOrNull
        if (version != null) requireSupported(version)
        return json.decodeFromJsonElement(tree)
    }

    private fun requireSupported(version: Int) {
        if (!BackupData.isSupported(version)) {
            throw UnsupportedBackupVersionException(found = version, supported = BackupData.CURRENT_VERSION)
        }
    }

    suspend fun import(data: BackupData) {
        requireSupported(data.version)
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
