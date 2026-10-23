package com.seblful.klas.testutil

import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.notifications.AlarmClock
import com.seblful.klas.notifications.ArmedReminders
import com.seblful.klas.notifications.ExactAlarmPermission
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.print.Sheet
import com.seblful.klas.print.SheetPrinter
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentSharing
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.storage.FolderGrants
import org.junit.rules.TestRule
import java.io.Closeable

class FakeCalendarPermission(var granted: Boolean = true) : CalendarPermission {
    override fun isGranted(): Boolean = granted
}

class FakeNotificationPermission(
    var granted: Boolean = true,
    override var canAsk: Boolean = true,
) : NotificationPermission {
    override fun isGranted(): Boolean = granted
}

class RecordingAlarmClock : AlarmClock {

    data class Armed(val classId: Long, val atMillis: Long, val exact: Boolean)

    val armed = mutableListOf<Armed>()
    val cancelled = mutableListOf<Long>()

    override fun setExact(classId: Long, atMillis: Long) {
        armed += Armed(classId, atMillis, exact = true)
    }

    override fun setInexact(classId: Long, atMillis: Long) {
        armed += Armed(classId, atMillis, exact = false)
    }

    override fun cancel(classId: Long) {
        cancelled += classId
    }

    /** Forgets the calls so far, for a test asserting what a *second* arming did. */
    fun clear() {
        armed.clear()
        cancelled.clear()
    }
}

class FakeArmedReminders(private var ids: Set<Long> = emptySet()) : ArmedReminders {
    override fun read(): Set<Long> = ids
    override fun write(ids: Set<Long>) {
        this.ids = ids
    }
}

class FakeExactAlarmPermission(var granted: Boolean = true) : ExactAlarmPermission {
    override fun isGranted(): Boolean = granted
}

class FakeDocumentStore(var failing: Boolean = false) : DocumentStore {
    val docs = mutableMapOf<String, String>()

    /** What was written as bytes — a PDF — by document. */
    val files = mutableMapOf<String, ByteArray>()

    override suspend fun read(ref: DocumentRef): String {
        if (failing) error("Cannot open input stream")
        return docs[ref.value] ?: error("No such document: ${ref.value}")
    }

    override suspend fun write(ref: DocumentRef, text: String) {
        if (failing) error("Cannot open output stream")
        docs[ref.value] = text
    }

    override suspend fun write(ref: DocumentRef, bytes: ByteArray) {
        if (failing) error("Cannot open output stream")
        files[ref.value] = bytes
    }
}

/** [FolderGrants] that remembers what is kept; [refusing] is a picker that granted nothing to keep. */
class FakeFolderGrants(var refusing: Boolean = false) : FolderGrants {
    val kept = mutableSetOf<String>()
    val names = mutableMapOf<String, String>()

    override fun keep(folder: String) {
        if (refusing) throw SecurityException("No persistable grant for $folder")
        kept += folder
    }

    override fun release(folder: String) {
        kept -= folder
    }

    override suspend fun name(folder: String): String? = names[folder]
}

/** [SheetPrinter] that keeps each sheet and prints a stand-in: the platform's PDF writer is the renderer's test. */
class RecordingSheetPrinter : SheetPrinter {
    val printed = mutableListOf<Sheet>()

    override fun print(sheet: Sheet): ByteArray {
        printed += sheet
        return "%PDF ${sheet.title}".encodeToByteArray()
    }
}

class RecordingDocumentSharing(var failing: Boolean = false) : DocumentSharing {
    class Shared(
        val filename: String,
        val mimeType: String,
        val subject: String,
        val bytes: ByteArray,
    ) {
        val text get() = bytes.decodeToString()
    }

    val shared = mutableListOf<Shared>()

    override suspend fun share(filename: String, mimeType: String, subject: String, bytes: ByteArray) {
        if (failing) error("Nothing can receive this")
        shared += Shared(filename, mimeType, subject, bytes)
    }
}

/**
 * The phone the fixtures assume: Russian, in Belarus — the app's original tutor.
 *
 * Which matters to exactly two answers, both of them about a settings row nobody has written yet: what
 * `AppLanguage.SYSTEM` resolves to, and which currency an amount is captioned with. A test about either
 * hands [TestEnv] a different list; every other test wants "the app as it shipped", which is this.
 */
val RussianMinskPhone: List<java.util.Locale> = listOf(java.util.Locale.forLanguageTag("ru-BY"))

class TestEnv(
    val database: TestDatabase = TestDatabase(),
    val deviceLocales: List<java.util.Locale> = RussianMinskPhone,
    clock: MutableClock = testClock(),
) : RepositoryTestEnv(database.db, database.transactor, clock, deviceLocales.toDevicePreferences()),
    TestRule by database,
    Closeable by database {
    val calendarPermission = FakeCalendarPermission()
    val notificationPermission = FakeNotificationPermission()
    val exactAlarmPermission = FakeExactAlarmPermission()
}
