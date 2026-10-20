package com.seblful.klas.data.backup

import com.seblful.klas.data.local.SnapshotDao
import com.seblful.klas.data.local.Transactor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Exports the whole database to a JSON string and restores it. Import is a full replace inside
 * a single transaction, so a failure leaves the existing data untouched (no partial restore).
 *
 * What it knows is the **file format** — the version, the JSON, and which of them this build will
 * accept. What tables there are, and the order they may be emptied and filled in, is the schema's and
 * lives with it ([SnapshotDao]). This used to hold seven DAOs and both foreign-key orderings itself,
 * which put a wipe of every table in reach of every repository that shared those DAOs.
 */
@SingleIn(AppScope::class)
class BackupManager @Inject constructor(
    private val transactor: Transactor,
    private val snapshot: SnapshotDao,
) {
    // Compact, not pretty-printed: indentation roughly doubled a large backup's size and memory.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(): BackupData = transactor.transaction { snapshot.read() }.toBackupData()

    suspend fun exportJson(): String = json.encodeToString(export())

    /**
     * The file as a [BackupData] — with its **version read first**, and a newer one refused before any row
     * is decoded.
     *
     * The rows' enum columns are typed, so a name this build has no constant for fails the decode — which
     * is right for a file of this build's version or older (it is malformed: no build ever wrote that name),
     * and wrong for a newer one: a later build that adds a status writes it into its files, and those have
     * to be refused as *newer*, telling the tutor to update the app, rather than as a broken file. So the
     * version is read before the rows. The full decode requires the version, subjects and students
     * envelope shared by every supported export, rejecting unrelated JSON before any replacement.
     */
    fun parse(text: String): BackupData {
        // Decoded straight from the text both times: a whole `JsonElement` tree of a large backup was its
        // biggest copy in memory. Anything [Header] cannot read is left to the decode to refuse.
        val version = runCatching { json.decodeFromString<Header>(text).version }.getOrNull()
            .let { (it as? JsonPrimitive)?.intOrNull }
        if (version != null) requireSupported(version)
        return json.decodeFromString(text)
    }

    /** A backup's version alone; every other key is skipped. */
    @Serializable
    private class Header(val version: JsonElement? = null)

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
