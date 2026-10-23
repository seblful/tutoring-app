package com.seblful.klas.data.backup

import com.seblful.klas.data.local.SnapshotDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.settings.SettingsRepository
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
import kotlin.time.Instant

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
    private val settings: SettingsRepository,
) {
    // Compact, not pretty-printed: indentation roughly doubled a large backup's size and memory.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The data, with the settings that give it its meaning ([BackupPreferences]). */
    suspend fun export(): BackupData {
        val rows = transactor.transaction { snapshot.read() }
        return rows.toBackupData().copy(preferences = settings.get().toBackupPreferences())
    }

    /** The backup as its file says it, stamped with [at] — the moment it was made — when given. */
    suspend fun exportJson(at: Instant? = null): String = encode(export(), at)

    /** [data] as a backup file, stamped with [at] when given. */
    fun encode(data: BackupData, at: Instant? = null): String =
        json.encodeToString(data.copy(exportedAtMillis = at?.toEpochMilliseconds()))

    /**
     * What [text] would restore, read the way [parse] reads it — so a file that will be refused is refused
     * here, before the tutor is asked anything.
     */
    fun preview(text: String): BackupPreview {
        val data = parse(text)
        return BackupPreview(
            exportedAt = data.exportedAtMillis?.let { Instant.fromEpochMilliseconds(it) },
            students = data.students.size,
            groups = data.groups.size,
            // A file older than v6 keeps its lessons as flat classes.
            lessons = data.lessons.size.takeIf { it > 0 } ?: data.classes.splitIntoLessons().first.size,
            payments = data.payments.size,
            homework = data.homework.size,
        )
    }

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
        val header = runCatching { json.decodeFromString<Header>(text) }.getOrNull()
        if (header?.klasSealed != null) throw PasswordNeededException()
        val version = (header?.version as? JsonPrimitive)?.intOrNull
        if (version != null) requireSupported(version)
        return json.decodeFromString(text)
    }

    /** Whether [text] is a sealed backup ([SealedBackup]) — one to ask the password of before reading. */
    fun isSealed(text: String): Boolean =
        runCatching { json.decodeFromString<Header>(text).klasSealed != null }.getOrDefault(false)

    /** A backup's version, and whether it is sealed ([SealedBackup]); every other key is skipped. */
    @Serializable
    private class Header(val version: JsonElement? = null, val klasSealed: JsonElement? = null)

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
