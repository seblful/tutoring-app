package com.seblful.klas.store

import android.app.Application
import android.content.Context
import com.seblful.klas.calendar.AndroidCalendarPermission
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.DeviceCalendarSync
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.di.AppGraph
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.di.DeviceLocales
import com.seblful.klas.di.FirstDayOfWeek
import com.seblful.klas.di.LocaleModule
import com.seblful.klas.di.TimeModule
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.notifications.DueDigest
import com.seblful.klas.notifications.DueReminder
import com.seblful.klas.work.WorkScheduler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import kotlinx.datetime.DayOfWeek
import java.util.Locale

/**
 * The production graph for [StoreScreenshots], less what a screenshot cannot use: the device clock and the
 * phone's locale and week ([TimeModule], [LocaleModule]), WorkManager ([WorkScheduler]) and the device
 * calendar. Each is handed to the factory instead; everything else — the database, the repositories, every
 * view model — is the app's own.
 */
@DependencyGraph(
    AppScope::class,
    excludes = [
        TimeModule::class,
        LocaleModule::class,
        WorkScheduler::class,
        DeviceCalendarSync::class,
        AndroidCalendarPermission::class,
    ],
)
internal interface StoreGraph : AppGraph {
    val seeder: DemoSeeder
    val settings: SettingsRepository
    val classes: ClassRepository
    val dueReminder: DueReminder
    val dueDigest: DueDigest

    @DependencyGraph.Factory
    fun interface Factory {
        @Suppress("LongParameterList") // One per binding swapped out, which is the point of this graph.
        fun create(
            @Provides @ApplicationContext context: Context,
            @Provides clock: WallClock,
            @Provides @FirstDayOfWeek firstDayOfWeek: DayOfWeek,
            @Provides @DeviceLocales deviceLocales: List<Locale>,
            @Provides reminders: ReminderScheduler,
            @Provides digests: DigestScheduler,
            @Provides calendar: CalendarSync,
            @Provides calendarPermission: CalendarPermission,
        ): StoreGraph
    }
}

/** What the store screenshots run as: an application whose graph the test builds once it knows the tutor. */
class StoreApp : Application(), AppGraph.Holder {
    override lateinit var graph: AppGraph
}
