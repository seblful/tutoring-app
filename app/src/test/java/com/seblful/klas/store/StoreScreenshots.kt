package com.seblful.klas.store

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.calendar.CalendarModule
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.di.DeviceLocales
import com.seblful.klas.di.FirstDayOfWeek
import com.seblful.klas.di.LocaleModule
import com.seblful.klas.di.TimeModule
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.notifications.DueDigest
import com.seblful.klas.notifications.DueReminder
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.work.WorkModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.text.DecimalFormatSymbols
import java.time.Clock
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import javax.imageio.ImageIO
import javax.inject.Inject

/**
 * The raw phone screenshots for the Play listing: the real app, its real screens, on [DemoTutor]'s data.
 *
 * **Not a test of anything** — it is the camera, and it lives in the unit suite only because that is where
 * the app can be run without a phone: Robolectric draws `MainActivity` with native graphics, Hilt wires
 * the production graph over the production database, and only the clock, the phone's locale and the
 * WorkManager-backed schedulers are swapped (a stopped clock is what makes «on now» land on the same
 * lesson every run). Skipped unless asked for, so `testDebugUnitTest` and CI never pay for it:
 *
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots
 * ```
 *
 * Each language writes `<out>/<lang>/<shot>.png` and a `shots.json` beside them naming the colours at
 * the top and bottom edge — what `store/render.mjs` paints the frame's status and gesture bars with, so
 * the phone around the screenshot is continuous with what is on it. See docs/store-listing.md.
 */
@OptIn(ExperimentalRoborazziApi::class)
@HiltAndroidTest
@UninstallModules(TimeModule::class, LocaleModule::class, WorkModule::class, CalendarModule::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = StoreScreenshots.PHONE)
class StoreScreenshots {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private var tutor: DemoTutor = DemoTutor.english

    @BindValue
    @JvmField
    val clock: Clock = Clock.fixed(DemoTutor.NOW.atZone(DemoTutor.ZONE).toInstant(), DemoTutor.ZONE)

    @BindValue
    @FirstDayOfWeek
    @JvmField
    var firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY

    @BindValue
    @DeviceLocales
    @JvmField
    var deviceLocales: List<Locale> = listOf(Locale.US)

    @BindValue
    @JvmField
    val reminders: ReminderScheduler = RecordingReminderScheduler()

    @BindValue
    @JvmField
    val digests: DigestScheduler = RecordingDigestScheduler()

    /** The device calendar has nothing behind it under Robolectric; the settings only need it to answer. */
    @BindValue
    @JvmField
    val calendar: CalendarSync = FakeCalendarSync()

    @BindValue
    @JvmField
    val calendarPermission: CalendarPermission = FakeCalendarPermission(granted = true)

    @Inject
    internal lateinit var seeder: DemoSeeder

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var classes: ClassRepository

    @Inject
    lateinit var dueReminder: DueReminder

    @Inject
    lateinit var dueDigest: DueDigest

    private val shots = mutableListOf<Shot>()
    private lateinit var out: File
    private val timeZone: TimeZone = TimeZone.getDefault()
    private val locale: Locale = Locale.getDefault()

    @Before
    fun zone() {
        TimeZone.setDefault(TimeZone.getTimeZone(DemoTutor.ZONE))
    }

    /** Puts back the JVM-wide defaults [shoot] moved, so the next test class sees the machine's own. */
    @After
    fun restoreDefaults() {
        TimeZone.setDefault(timeZone)
        Locale.setDefault(locale)
    }

    @Test
    fun english() = shoot(DemoTutor.english, "en-rUS")

    @Test
    fun russian() = shoot(DemoTutor.russian, "ru-rRU")

    private fun shoot(tutor: DemoTutor, qualifier: String) {
        val root = System.getProperty(OUT_PROPERTY)
        assumeTrue("Store screenshots are taken only with -PstoreScreenshots", root != null)
        out = File(root!!, tutor.locale.toLanguageTag()).apply { deleteRecursively(); mkdirs() }

        this.tutor = tutor
        firstDayOfWeek = tutor.firstDayOfWeek
        deviceLocales = listOf(tutor.locale)
        // The whole phone speaks the language, not just the app's resources: `MainActivity` and the week's
        // first day read the phone's default locale, as they would on a Russian or an American phone.
        Locale.setDefault(tutor.locale)
        RuntimeEnvironment.setQualifiers("+$qualifier")
        hilt.inject()
        runBlocking { seeder.seed(tutor) }

        launchOnToday()
        writeManifest()
    }

    /**
     * Opens the app and takes the tour once the schedule shows a lesson «on now».
     *
     * Under Robolectric the schedule's once-a-minute clock now and then never reaches a freshly started
     * screen — about one run in four, never twice in a row, and not something a phone does — which leaves
     * every lesson «planned» and no «now» line. A new activity is a new set of view models and a new clock,
     * so the first screen gets [LAUNCH_ATTEMPTS] tries rather than a longer wait.
     */
    private fun launchOnToday() {
        val onNow = string(R.string.class_status_in_progress)
        for (attempt in 1..LAUNCH_ATTEMPTS) {
            ActivityScenario.launch(MainActivity::class.java).use {
                if (appears(onNow, FIRST_SCREEN_MS) || attempt == LAUNCH_ATTEMPTS) {
                    tour()
                    return
                }
            }
        }
    }

    /**
     * Scrolls the screen's list until the row reading [text] sits at its top. `performScrollTo` scrolls only
     * as far as it must, which leaves a row just inside the bottom edge and half of another under the bar.
     */
    private fun scrollToTop(text: String) {
        val list = compose.onNode(hasScrollAction())
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val rowTop = compose.onNode(hasText(text)).fetchSemanticsNode().boundsInRoot.top
        val breathingRoom = with(compose.density) { ROW_TOP_MARGIN.toPx() }
        list.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, rowTop - listTop - breathingRoom)
        }
        settle()
    }

    private fun appears(text: String, timeoutMs: Long): Boolean = try {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        true
    } catch (_: ComposeTimeoutException) {
        false
    }

    /** Every screen worth showing, in the order a tutor would meet them. */
    private fun tour() {
        val student = tutor.students[1].name
        val group = tutor.group.name

        // The schedule opens on today: taught, on now, still to come.
        waitFor(string(R.string.class_status_in_progress))
        capture("schedule-day")

        // A lesson's sheet: what it is, who is in it, the homework riding on it.
        click(group)
        settle()
        capture("lesson-sheet")
        back()

        click(string(R.string.schedule_view_week))
        settle()
        capture("schedule-week")

        click(string(R.string.schedule_view_month))
        settle()
        capture("schedule-month")
        click(string(R.string.schedule_view_day))

        tab(R.string.nav_people)
        waitFor(student)
        capture("people")

        click(student)
        waitFor(string(R.string.stat_debt))
        capture("student")
        back()

        click(string(R.string.groups_title))
        waitFor(group)
        click(group)
        settle()
        capture("group")
        back()

        tab(R.string.nav_homework)
        waitFor(tutor.group.homework)
        capture("homework")

        // The month rather than the year it opens on: the year's total is the one figure on these screens
        // that would run past a thousand.
        tab(R.string.nav_reports)
        waitFor(string(R.string.reports_total_income))
        compose.onNode(hasContentDescription(string(R.string.reports_period))).performClick()
        click(string(R.string.reports_period_month))
        settle()
        capture("reports")

        tab(R.string.nav_more)
        settle()
        capture("more")

        // Reminders and the calendar, switched on as a tutor who uses them has them.
        switchOnReminders()
        click(string(R.string.settings_title))
        waitFor(string(R.string.settings_notifications_desc))
        capture("settings")
        scrollToTop(string(R.string.settings_section_calendar))
        capture("settings-calendar")
        back()
        recordNotifications()

        // The same day after dark, for the listing's dark-theme shot.
        runBlocking { settings.update { it.copy(themeMode = ThemeChoice.DARK) } }
        tab(R.string.nav_schedule)
        waitFor(string(R.string.class_status_in_progress))
        capture("schedule-day-dark", dark = true)
        tab(R.string.nav_reports)
        waitFor(string(R.string.reports_total_income))
        capture("reports-dark", dark = true)
    }

    /**
     * Calendar sync, its invitations, the morning agenda and the weekly debt summary on, and every
     * permission they need granted — what a tutor sees once they have set reminders up, rather than the
     * warnings a fresh install shows.
     */
    private fun switchOnReminders() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        runBlocking {
            settings.update {
                it.copy(
                    calendarSyncEnabled = true,
                    emailInvitesEnabled = true,
                    dailyAgendaEnabled = true,
                    debtDigestEnabled = true,
                )
            }
        }
        settle()
    }

    /**
     * Posts what the tray would hold today through the app's own path — the reminder for the group's
     * lesson (with the homework riding on it), the morning agenda and the weekly debt summary — and writes
     * what `AndroidNotifier` put in each to `notifications.json`, so the lock-screen picture carries the
     * app's own words rather than ones written for it.
     */
    private fun recordNotifications() {
        val now = DemoTutor.NOW.atZone(DemoTutor.ZONE)
        val reminderAt = runBlocking {
            val day = now.toLocalDate().atStartOfDay(DemoTutor.ZONE).toInstant()
            val group = classes.getBetween(day, day.plusSeconds(SECONDS_PER_DAY))
                .first { it.groupId != null && it.start.isAfter(clock.instant()) }
            val lead = settings.get().reminderLeadMinutes.toLong()
            val due = group.start.minusSeconds(lead * SECONDS_PER_MINUTE)
            check(dueReminder.post(group.id, due)) { "no reminder" }
            check(dueDigest.postAgenda(day.plusSeconds(AGENDA_HOUR * SECONDS_PER_HOUR))) { "no agenda" }
            check(dueDigest.postDebts()) { "no debt summary" }
            due
        }

        val app = ApplicationProvider.getApplicationContext<Application>()
        // A group's summary is Android's bookkeeping for a stack of reminders, never shown as a card of its own.
        val tray = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            .filterNot { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
        val entries = tray.joinToString(",\n") { n ->
            val extras = n.extras
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }
            val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            val actions = n.actions.orEmpty().map { it.title.toString() }
            """  { "title": ${json(extras.getCharSequence(Notification.EXTRA_TITLE))}, "text": ${json(body)}, """ +
                """"lines": ${lines.joinToString(", ", "[", "]") { json(it) }}, """ +
                """"actions": ${actions.joinToString(", ", "[", "]") { json(it) }} }"""
        }
        // The lock screen reads the moment the reminder arrives.
        val lockTime = reminderAt.atZone(DemoTutor.ZONE).toLocalTime()
        val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", tutor.locale))
            .replaceFirstChar { it.titlecase(tutor.locale) }
        val clockText = lockTime.format(
            DateTimeFormatter.ofPattern(if (tutor.locale.language == "en") "h:mm" else "HH:mm", tutor.locale),
        )
        File(out, "notifications.json").writeText(
            // The release build's name: this is the debug build, whose English label says so.
            "{\n  \"app\": \"Klas\", \"now\": ${json(string(R.string.schedule_now))}, \"date\": ${json(date)}, " +
                "\"clock\": ${json(clockText)},\n  \"notifications\": [\n$entries\n  ]\n}\n",
        )
    }

    private fun json(text: CharSequence?): String = buildString {
        append('"')
        text?.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
        append('"')
    }

    private fun capture(name: String, dark: Boolean = false) {
        settle()
        checkAmounts(name)
        val file = File(out, "$name.png")
        captureScreenRoboImage(file, RoborazziOptions(taskType = RoborazziTaskType.Record))
        val image = ImageIO.read(file)
        val probeX = image.width / 2
        shots += Shot(
            name = name,
            top = image.getRGB(probeX, 1),
            bottom = image.getRGB(probeX, image.height - 2),
            dark = dark,
        )
    }

    /**
     * Every amount on screen is at most [MAX_AMOUNT]: the listing shows a private tutor's week, and a
     * five-figure total reads as a business's books. Fails rather than shooting a screen that breaks it.
     */
    private fun checkAmounts(name: String) {
        val symbols = DecimalFormatSymbols.getInstance(tutor.locale)
        // A backslash makes either separator literal, even inside a character class.
        val group = "\\" + symbols.groupingSeparator
        val decimal = "\\" + symbols.decimalSeparator
        val amount = Regex("""(-?\d[\d\s$group]*(?:$decimal\d+)?)\s${tutor.currency.name}""")
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        val tooLarge = texts.flatMap { text ->
            amount.findAll(text).mapNotNull { match ->
                val value = match.groupValues[1]
                    .filterNot { it.isWhitespace() || it == symbols.groupingSeparator }
                    .replace(symbols.decimalSeparator, '.')
                    .toBigDecimal()
                match.value.takeIf { value.abs() > MAX_AMOUNT.toBigDecimal() }
            }
        }
        assertTrue("«$name» shows amounts over $MAX_AMOUNT: $tooLarge", tooLarge.isEmpty())
    }

    private fun writeManifest() {
        val entries = shots.joinToString(",\n") { s ->
            """  "${s.name}": { "top": "${hex(s.top)}", "bottom": "${hex(s.bottom)}", "dark": ${s.dark} }"""
        }
        File(out, "shots.json").writeText("{\n$entries\n}\n")
    }

    private fun tab(@StringRes label: Int) {
        compose.onNode(hasText(string(label)) and isSelectable()).performClick()
        settle()
    }

    private fun click(text: String) {
        firstClickable(text).performClick()
    }

    private fun firstClickable(text: String): SemanticsNodeInteraction {
        waitFor(text)
        val clickable = compose.onAllNodes(hasText(text) and hasClickAction())
        return if (clickable.fetchSemanticsNodes().isNotEmpty()) {
            clickable[0]
        } else {
            compose.onAllNodesWithText(text, useUnmergedTree = true)[0]
        }
    }

    private fun back() {
        // Espresso's back goes to the focused window, which is what closes a bottom sheet — a sheet is a
        // window of its own, out of the activity's back dispatcher's reach.
        Espresso.pressBack()
        settle()
    }

    private fun waitFor(text: String) {
        try {
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            val seen = File(out, "_failed.png")
            captureScreenRoboImage(seen, RoborazziOptions(taskType = RoborazziTaskType.Record))
            throw AssertionError("Never saw «$text» on screen — what was there: $seen", e)
        }
    }

    /** Lets the cross-fades, the sheet's slide and the skeleton's grace all finish. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun string(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id)

    private data class Shot(val name: String, val top: Int, val bottom: Int, val dark: Boolean)

    private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

    companion object {
        /**
         * A 1080 × 2400-class phone's 412 × 915 dp, drawn at 4× (1648 px wide): the listing shows the screen
         * about 1450 px wide, so the capture has more pixels than it is shown at and is never stretched.
         */
        const val PHONE = "w412dp-h915dp-xxxhdpi"

        /** Set by `-PstoreScreenshots` (app/build.gradle.kts); where the raw shots are written. */
        const val OUT_PROPERTY = "klas.store.out"

        const val TIMEOUT_MS = 60_000L

        /** How long the first screen gets to show «on now» before the app is opened again. */
        const val FIRST_SCREEN_MS = 20_000L
        const val LAUNCH_ATTEMPTS = 3

        /** Room left above a row [scrollToTop] brings up, so a section title is not flush with the bar. */
        val ROW_TOP_MARGIN = 12.dp
        const val SETTLE_MS = 2_000L

        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3_600L
        const val SECONDS_PER_DAY = 86_400L

        /** When the morning agenda is posted, as in the app's default. */
        const val AGENDA_HOUR = 9L

        /** The largest amount a store screenshot may show, in whichever currency. */
        const val MAX_AMOUNT = 1_000
    }
}
