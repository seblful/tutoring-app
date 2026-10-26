package com.seblful.klas

import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.backup.RestoreSnapshots
import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.local.KlasDatabase
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.lessons.LessonCompletions
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.queries.groups.GroupDetailsQuery
import com.seblful.klas.queries.homework.HomeworkQuery
import com.seblful.klas.queries.lessons.TodayLessonsQuery
import com.seblful.klas.queries.reports.ReportsQuery
import com.seblful.klas.queries.students.StudentDetailsQuery
import com.seblful.klas.settings.DevicePreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/** Reusable entry points backed by one database and one set of host adapters. */
interface KlasStore {
    val students: StudentRepository
    val subjects: SubjectRepository
    val groups: GroupRepository
    val enrollment: EnrollmentRepository
    val classes: ClassRepository
    val lessonCompletions: LessonCompletions
    val payments: PaymentRepository
    val homework: HomeworkRepository
    val homeworkQuery: HomeworkQuery
    val settings: SettingsRepository
    val backup: BackupManager
    val restore: BackupRestore
    val todayLessons: TodayLessonsQuery
    val groupDetails: GroupDetailsQuery
    val studentDetails: StudentDetailsQuery
    val reports: ReportsQuery
}

/** Platform-owned behavior supplied when opening a store outside an Android graph. */
interface HostServices {
    val preferences: DevicePreferences
    val clock: WallClock
    val calendar: CalendarSync
    val reminders: ReminderScheduler
    val digests: DigestScheduler
    val notifier: Notifier
    val restoreSnapshots: RestoreSnapshots
}

/** The caller owns [database] and closes it after all store work and collectors stop. */
fun createKlasStore(database: KlasDatabase, host: HostServices): KlasStore =
    createGraphFactory<StoreGraph.Factory>().create(database, host)

@DependencyGraph(AppScope::class)
internal interface StoreGraph : KlasStore {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides database: KlasDatabase, @Includes host: HostServices): StoreGraph
    }
}
