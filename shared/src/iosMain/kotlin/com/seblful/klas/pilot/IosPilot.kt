package com.seblful.klas.pilot

import androidx.room.Room
import com.seblful.klas.HostServices
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.createKlasStore
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.KlasDatabase
import com.seblful.klas.data.local.buildDatabase
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.settings.DevicePreferences
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlin.time.Clock

/** Callback interface for the pilot's SwiftUI host; completions run on the main thread. */
class IosPilot
@Throws(Exception::class)
constructor(databaseName: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val database = pilotDatabase(databaseName)
    private val workflow = PilotWorkflow(createKlasStore(database, PilotHost))

    fun load(completion: (PilotSnapshot?, String?) -> Unit) = perform(completion) { load() }

    fun addStudent(name: String, completion: (PilotSnapshot?, String?) -> Unit) =
        perform(completion) { addStudent(name) }

    fun book(studentId: Long, startsAtMillis: Long, completion: (PilotSnapshot?, String?) -> Unit) =
        perform(completion) { book(studentId, startsAtMillis) }

    /** Call after requests finish; the SwiftUI host owns one instance for its application lifetime. */
    fun close() {
        scope.cancel()
        database.close()
    }

    private fun perform(
        completion: (PilotSnapshot?, String?) -> Unit,
        action: suspend PilotWorkflow.() -> PilotSnapshot,
    ) {
        scope.launch {
            val result = runCatchingCancellable { workflow.action() }
            result.fold({ completion(it, null) }, { completion(null, it.message ?: "Could not save the change.") })
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun pilotDatabase(name: String): KlasDatabase {
    require(Regex("[a-zA-Z0-9-]+\\.db").matches(name)) { "Invalid pilot database name" }
    val directory = NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, null, true, null,
    )
    val path = requireNotNull(directory?.path) { "Application Support is unavailable" }
    return KlasDatabase(buildDatabase(Room.databaseBuilder<AppDatabase>(name = "$path/$name")))
}

/** The pilot deliberately offers local persistence only; it does not request device permissions. */
private object PilotHost : HostServices {
    override val preferences = DevicePreferences(listOf("en-US"), listOf("USD"))
    override val clock = object : WallClock {
        override val zone: TimeZone get() = TimeZone.currentSystemDefault()
        override fun now() = Clock.System.now()
    }
    override val calendar = object : CalendarSync {
        override suspend fun upsert(
            klass: TutoringClass,
            title: String,
            guest: Guest?,
            reminderLeadMinutes: Int,
            description: String,
        ): String? = null
        override suspend fun refresh(klass: TutoringClass, title: String, description: String) = Unit
        override suspend fun delete(eventId: String) = Unit
    }
    override val reminders = object : ReminderScheduler {
        override fun refreshNow() = Unit
        override fun ensurePeriodicMaintenance() = Unit
        override fun schedule(plans: List<Reminders.Plan>) = Unit
    }
    override val digests = object : DigestScheduler {
        override suspend fun schedule(plan: Digests.Plan) = Unit
    }
    override val notifier = object : Notifier {
        override fun ensureChannels() = Unit
        override fun showClassReminder(
            klass: TutoringClass,
            whoName: String,
            subjectName: String,
            homeworkToCheck: String?,
        ) = Unit
        override fun cancelClassReminder(classId: Long) = Unit
        override fun showAgenda(agenda: Digests.Agenda, currency: Currency) = Unit
        override fun showUnfinished(unfinished: Digests.Unfinished) = Unit
        override fun showDebts(debts: Digests.Debts, currency: Currency) = Unit
        override fun showAutoCompleted(lessons: Int, billed: Money, day: LocalDate, currency: Currency) = Unit
    }
}
