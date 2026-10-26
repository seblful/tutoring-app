package com.seblful.klas.store

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = StoreApp::class, qualifiers = StoreScreenshots.PHONE)
class StoreScreenshots(play: String) {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val tutor: DemoTutor = DemoTutor.all.single { it.play == play }

    /** Right to left, as Arabic is: the row swipes the other way. */
    private val rtl = TextUtils.getLayoutDirectionFromLocale(tutor.locale) == View.LAYOUT_DIRECTION_RTL

    private lateinit var graph: StoreGraph

    private lateinit var seeded: DemoSeeder.Seeded

    /** Who the lesson left unmarked is with ([DemoSeeder.Seeded.bookUnmarkedLesson]): Today's one to mark. */
    private lateinit var unmarked: String

    private val settings get() = graph.settings
    private val classes get() = graph.classes
    private val dueReminder get() = graph.dueReminder
    private val dueDigest get() = graph.dueDigest

    private val shots = mutableListOf<Shot>()
    private lateinit var out: File
    private val timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    private val locale: Locale = Locale.getDefault()

    @Before
    fun zone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(DemoTutor.ZONE.id))
    }

    /** Puts back the JVM-wide defaults [shoot] moved, so the next test class sees the machine's own. */
    @After
    fun restoreDefaults() {
        java.util.TimeZone.setDefault(timeZone)
        Locale.setDefault(locale)
    }

    @Test
    fun shoot() {
        val root = System.getProperty(OUT_PROPERTY)
        assumeTrue("Store screenshots are taken only with -PstoreScreenshots", root != null)
        out = File(root!!, tutor.play).apply { deleteRecursively(); mkdirs() }

        Locale.setDefault(tutor.locale)
        val region = tutor.locale.country.takeIf { it.isNotEmpty() }?.let { "-r$it" }.orEmpty()
        RuntimeEnvironment.setQualifiers("+${tutor.locale.language}$region${if (rtl) "-ldrtl" else ""}")
        graph = createGraphFactory<StoreGraph.Factory>().create(
            context = ApplicationProvider.getApplicationContext(),
            clock = DemoTutor.CLOCK,
            firstDayOfWeek = tutor.firstDayOfWeek,
            deviceLocales = listOf(tutor.locale),
            reminders = RecordingReminderScheduler(),
            digests = RecordingDigestScheduler(),
            calendar = FakeCalendarSync(),
            calendarPermission = FakeCalendarPermission(granted = true),
        )
        ApplicationProvider.getApplicationContext<StoreApp>().graph = graph
        seeded = runBlocking { graph.seeder.seed(tutor) }
        unmarked = runBlocking { seeded.bookUnmarkedLesson() }

        launchOnToday()
        writeManifest()
    }

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

    private fun scrollToTop(text: String) {
        val list = compose.onNode(hasScrollAction())
        val bounds = list.fetchSemanticsNode().boundsInRoot
        // A row the list has composed past its end but not placed yet measures at 0: further down than the
        // list is tall, as the reports' «By student» is in Arabic, whose longer lines push it down.
        val rowTop = compose.onNode(hasText(text)).fetchSemanticsNode().boundsInRoot.top
            .takeIf { it > bounds.top } ?: Float.MAX_VALUE
        val breathingRoom = with(compose.density) { ROW_TOP_MARGIN.toPx() }
        // The video joins the screen before and after into one list, so the two must overlap above the bar.
        val most = bounds.height - with(compose.density) { KEEP_IN_VIEW.toPx() }
        list.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, minOf(rowTop - bounds.top - breathingRoom, most))
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

    private fun tour() {
        val student = tutor.debtor.name
        val group = tutor.group.name

        // The app opens on Today: the lesson on now leads, and the one over and not marked is under it.
        waitFor(string(R.string.today_to_mark))
        capture("today", marks = mapOf("next" to nowCard(), "toMark" to row(unmarked)), firsts = setOf("toMark"))

        scheduleTour()

        tab(R.string.nav_people)
        waitFor(student)
        val credit = tutor.inCredit.name
        liftUp(student)
        capture("people", marks = mapOf("debtor" to row(student), "credit" to row(credit)))

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
        val add = hasContentDescription(string(R.string.homework_add)) and hasClickAction()
        capture("homework", marks = mapOf("first" to row(tutor.group.homework), "add" to add))

        reportsTour()

        switchOnReminders()
        gear()
        waitFor(string(R.string.settings_lessons_title))
        capture("settings")
        click(string(R.string.settings_calendar_title))
        waitFor(string(R.string.settings_calendar_sync_desc))
        capture(
            "settings-calendar",
            marks = mapOf(
                "sync" to row(string(R.string.settings_calendar_sync)),
                "invites" to row(string(R.string.settings_email_invites)),
            ),
        )
        back()
        back()
        recordNotifications()

        darkTour()
        filmTour()
    }

    private fun scheduleTour() {
        tab(R.string.nav_schedule)
        waitFor(string(R.string.class_status_in_progress))
        capture("schedule-day", marks = mapOf("onNow" to row(tutor.onNow.name)))

        click(tutor.group.name)
        settle()
        // The sheet's buttons: «Taught» and «Edit» told from the day's badges and rows by «Reschedule» beside them.
        val move = string(R.string.class_move)
        val beside = hasAnySibling(hasText(move))
        capture(
            "lesson-sheet",
            marks = mapOf(
                "taught" to (hasText(string(R.string.class_status_completed)) and hasClickAction() and beside),
                "move" to (hasText(move) and hasClickAction()),
                "edit" to (hasText(string(R.string.action_edit)) and hasClickAction() and beside),
            ),
        )
        back()

        click(string(R.string.schedule_view_week))
        settle()
        capture("schedule-week")

        click(string(R.string.schedule_view_month))
        settle()
        capture("schedule-month")
        click(string(R.string.schedule_view_day))
    }

    private fun reportsTour() {
        val label = string(R.string.reports_total_income)
        val byStudent = string(R.string.reports_by_student)
        tab(R.string.nav_reports)
        waitFor(label)
        compose.onNode(hasContentDescription(string(R.string.reports_period))).performClick()
        click(string(R.string.reports_period_month))
        settle()
        val list = hasScrollAction()
        val total = hasAnySibling(hasText(label)) and hasText(tutor.currency.symbol, substring = true)
        val bar = hasAnyChild(hasText(string(R.string.nav_reports)) and isSelectable())
        capture(
            "reports",
            marks = mapOf("income" to hasText(label), "list" to list, "anchor" to hasText(byStudent), "bar" to bar),
            texts = mapOf("total" to total),
        )
        scrollToTop(byStudent)
        capture("film-reports-end", marks = mapOf("list" to list, "anchor" to hasText(byStudent)))
        compose.onNode(list).performScrollToIndex(0)
        settle()
    }

    private fun darkTour() {
        val dark = hasText(string(R.string.theme_dark)) and isSelectable()
        gear()
        waitFor(string(R.string.settings_appearance_title))
        click(string(R.string.settings_appearance_title))
        waitFor(string(R.string.settings_language))
        compose.onNode(dark).performScrollTo()
        capture("film-theme", marks = mapOf("dark" to dark))
        compose.onNode(dark).performClick()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(dark and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        settle()
        capture("film-theme-dark", dark = true, marks = mapOf("dark" to dark))
        back()
        back()

        runBlocking { settings.update { it.copy(themeMode = ThemeChoice.DARK) } }
        tab(R.string.nav_today)
        waitFor(string(R.string.today_to_mark))
        capture("today-dark", dark = true)
        tab(R.string.nav_schedule)
        waitFor(string(R.string.class_status_in_progress))
        capture("schedule-day-dark", dark = true)
        tab(R.string.nav_reports)
        waitFor(string(R.string.reports_total_income))
        capture("reports-dark", dark = true)
        runBlocking { settings.update { it.copy(themeMode = ThemeChoice.LIGHT) } }
    }

    private fun filmTour() {
        val student = tutor.onNow.name
        val unmarked = row(unmarked)
        tab(R.string.nav_schedule)
        waitFor(string(R.string.class_status_in_progress))
        capture("film-day", marks = mapOf("swipe" to unmarked))
        swipe(unmarked, "film-swipe")
        capture("film-swiped", marks = mapOf("swipe" to unmarked))

        val homework = row(student)
        val add = hasContentDescription(string(R.string.homework_add)) and hasClickAction()
        tab(R.string.nav_homework)
        waitFor(tutor.group.homework)
        capture("film-homework-list", marks = mapOf("swipe" to homework, "add" to add))
        swipe(homework, "film-homework-swipe")
        capture("film-homework-done", marks = mapOf("add" to add))

        runBlocking { seeded.giveTypedHomework() }
        waitFor(tutor.typedHomework)
        capture("film-homework", marks = mapOf("added" to row(tutor.typedHomework)))

        // The widgets as the day now stands, the swiped lesson taught; then the lesson on now marked taught,
        // as the video's tap on the widget's tick does, for the dark Today the video ends on.
        StoreWidgets(graph, tutor.locale, out).capture().forEach { (name, marks) ->
            shots += Shot(name, top = 0, bottom = 0, dark = false, marks = marks, texts = emptyMap())
        }
        runBlocking {
            markOnNowTaught()
            settings.update { it.copy(themeMode = ThemeChoice.DARK) }
        }
        tab(R.string.nav_today)
        waitFor(string(R.string.today_lessons))
        capture("film-today-dark", dark = true)
        runBlocking { settings.update { it.copy(themeMode = ThemeChoice.LIGHT) } }
        File(out, "film.json").writeText(
            "{ \"student\": ${json(student)}, \"homework\": ${json(tutor.typedHomework)} }\n",
        )
    }

    private suspend fun markOnNowTaught() {
        val now = DemoTutor.CLOCK.now()
        val day = DemoTutor.NOW.date.atStartOfDayIn(DemoTutor.ZONE)
        val lesson = classes.getBetween(day, day.plus(SECONDS_PER_DAY.seconds))
            .single { it.start <= now && now < it.end }
        classes.setStatus(classes.getSessionFor(lesson.id)!!, LessonStatus.COMPLETED)
    }

    private fun swipe(matcher: SemanticsMatcher, name: String) {
        val row = compose.onNode(matcher)
        val home = row.fetchSemanticsNode().positionOnScreen.x
        val width = row.fetchSemanticsNode().size.width
        // Toward the row's end: rightward, or leftward right to left.
        val toEnd = if (rtl) -1f else 1f
        fun dragged() = (compose.onNode(matcher).fetchSemanticsNode().positionOnScreen.x - home) * toEnd
        fun dragTo(fraction: Float, step: Float) {
            // Bounded, so a row that stops following the finger fails the capture rather than hanging it.
            repeat((width / step).toInt()) {
                if (dragged() >= width * fraction) return
                row.performTouchInput { moveBy(Offset(step * toEnd, 0f)) }
            }
            val seen = File(out, "_failed.png")
            captureScreenRoboImage(seen, RoborazziOptions(taskType = RoborazziTaskType.Record))
            error("«$name»: the row moved ${dragged()} px of ${width * fraction}; what was there: $seen")
        }
        // Pressed and dragged past the touch slop in one batch: between batches the test's clock runs on, and a
        // press held that long picks the row (long-press picking) instead of swiping it.
        row.performTouchInput {
            down(Offset(if (rtl) width * (1 - SWIPE_FROM) else width * SWIPE_FROM, height / 2f))
            moveBy(Offset((viewConfiguration.touchSlop + SWIPE_STEP) * toEnd, 0f))
        }
        dragTo(SWIPE_MARK - SHORT_OF_MARK, SWIPE_STEP)
        capture("$name-unarmed", marks = mapOf("swipe" to matcher))
        dragTo(SWIPE_ARMED, SWIPE_STEP * 4)
        capture("$name-armed", marks = mapOf("swipe" to matcher))
        row.performTouchInput { up() }
        settle()
    }

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

    private fun recordNotifications() {
        val now = DemoTutor.NOW
        val reminderAt = runBlocking {
            val day = now.date.atStartOfDayIn(DemoTutor.ZONE)
            val group = classes.getBetween(day, day.plus(SECONDS_PER_DAY.seconds))
                .first { it.groupId != null && it.start > DemoTutor.CLOCK.now() }
            val lead = settings.get().reminderLeadMinutes.toLong()
            val due = group.start.minus((lead * SECONDS_PER_MINUTE).seconds)
            check(dueReminder.post(group.id, due)) { "no reminder" }
            check(dueDigest.postAgenda(day.plus((AGENDA_HOUR * SECONDS_PER_HOUR).seconds))) { "no agenda" }
            check(dueDigest.postDebts()) { "no debt summary" }
            due
        }

        val app = ApplicationProvider.getApplicationContext<Application>()
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
        val lockTime = reminderAt.toLocalDateTime(DemoTutor.ZONE).time
        val date = now.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", tutor.locale))
            .replaceFirstChar { it.titlecase(tutor.locale) }
        // The phone's clock, 12- or 24-hour as the app reads it (as do [StoreWidgets]): the lock screen's at
        // the reminder, the status bar's at [DemoTutor.NOW].
        val clock = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(app)) "HH:mm" else "h:mm", tutor.locale)
        File(out, "notifications.json").writeText(
            "{\n  \"app\": \"Klas\", \"now\": ${json(string(R.string.schedule_now))}, \"date\": ${json(date)}, " +
                "\"clock\": ${json(lockTime.toJavaLocalTime().format(clock))}, " +
                "\"statusClock\": ${json(now.time.toJavaLocalTime().format(clock))},\n" +
                "  \"notifications\": [\n$entries\n  ]\n}\n",
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

    private fun capture(
        name: String,
        dark: Boolean = false,
        marks: Map<String, SemanticsMatcher> = emptyMap(),
        texts: Map<String, SemanticsMatcher> = emptyMap(),
        firsts: Set<String> = emptySet(),
    ) {
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
            marks = marks.mapValues { (mark, node) -> bounds(name, mark, node, first = mark in firsts) },
            texts = texts.mapValues { (mark, node) -> textStyle(name, mark, node) },
        )
    }

    private fun row(text: String) = hasText(text, substring = true) and hasClickAction()

    /** Today's card for the lesson on now, which the whole card opens. */
    private fun nowCard() = hasText(string(R.string.today_now)) and hasClickAction()

    /** [matcher]'s box on screen; with [first], the first of several in reading order, else the only one. */
    private fun bounds(shot: String, mark: String, matcher: SemanticsMatcher, first: Boolean = false): List<Int> {
        val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
        check(nodes.size == 1 || (first && nodes.isNotEmpty())) {
            "«$shot» mark «$mark» matches ${nodes.size} nodes, not one"
        }
        val node = nodes.first()
        val at = node.positionOnScreen
        val size = node.size.toSize()
        val corner = (node.config.getOrNull(SemanticsProperties.Shape) as? CornerBasedShape)
            ?.topStart?.toPx(size, compose.density) ?: 0f
        return listOf(at.x, at.y, at.x + size.width, at.y + size.height, corner).map { it.toInt() }
    }

    private fun textStyle(shot: String, mark: String, matcher: SemanticsMatcher): String {
        val nodes = compose.onAllNodes(matcher).fetchSemanticsNodes()
        check(nodes.size == 1) { "«$shot» text «$mark» matches ${nodes.size} nodes, not one" }
        val node = nodes.single()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.singleOrNull() ?: error("«$shot» text «$mark» has no text layout")
        check(layout.lineCount == 1) { "«$shot» text «$mark» runs to ${layout.lineCount} lines, not one" }
        val style = layout.layoutInput.style
        val size = with(compose.density) { style.fontSize.toPx() }
        val spacing = style.letterSpacing.let {
            when {
                it.isSp -> with(compose.density) { it.toPx() }
                it.isEm -> it.value * size
                else -> 0f
            }
        }
        val at = node.positionOnScreen
        return "{ \"text\": ${json(layout.layoutInput.text.text)}, \"size\": $size, " +
            "\"weight\": ${(style.fontWeight ?: FontWeight.Normal).weight}, " +
            "\"color\": \"${hex(style.color.toArgb())}\", \"spacing\": $spacing, " +
            "\"left\": ${at.x + layout.getLineLeft(0)}, \"right\": ${at.x + layout.getLineRight(0)}, " +
            "\"top\": ${at.y + layout.getLineTop(0)}, \"bottom\": ${at.y + layout.getLineBottom(0)}, " +
            "\"baseline\": ${at.y + layout.firstBaseline} }"
    }

    private fun checkAmounts(name: String) {
        val symbols = DecimalFormatSymbols.getInstance(tutor.locale)
        val limit = MAX_AMOUNT * tutor.scale
        val group = "\\" + symbols.groupingSeparator
        val decimal = "\\" + symbols.decimalSeparator
        val currency = Regex.escape(tutor.currency.symbol)
        // On either side of the sign, as the language writes it: "$90", «90 Br». (?U): every script's digits.
        val number = """-?\d[\d\s$group]*(?:$decimal\d+)?"""
        val amount = Regex("""(?U)$currency\s?($number)|($number)\s?$currency""")
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        val tooLarge = texts.flatMap { text ->
            amount.findAll(text).mapNotNull { match ->
                val value = match.groupValues.drop(1).first { it.isNotEmpty() }
                    .mapNotNull { c ->
                        when {
                            c.isDigit() -> '0' + Character.digit(c, DECIMAL)
                            c == symbols.decimalSeparator -> '.'
                            else -> null
                        }
                    }
                    .joinToString("")
                    .toBigDecimal()
                match.value.takeIf { value > limit.toBigDecimal() }
            }
        }
        assertTrue("«$name» shows amounts over $limit: $tooLarge", tooLarge.isEmpty())
    }

    private fun writeManifest() {
        val entries = shots.joinToString(",\n") { s ->
            val marks = s.marks.entries.joinToString(", ", "{ ", " }") { (mark, box) -> "\"$mark\": $box" }
            val texts = s.texts.entries.joinToString(", ", "{ ", " }") { (mark, style) -> "\"$mark\": $style" }
            """  "${s.name}": { "top": "${hex(s.top)}", "bottom": "${hex(s.bottom)}", "dark": ${s.dark}, """ +
                """"marks": $marks, "texts": $texts }"""
        }
        File(out, "shots.json").writeText("{\n$entries\n}\n")
    }

    /** Settings, through the gear on the tab's bar. */
    private fun gear() {
        compose.onAllNodes(hasContentDescription(string(R.string.settings_title)) and hasClickAction())
            .onFirst().performClick()
        settle()
    }

    private fun tab(@StringRes label: Int) {
        compose.onNode(hasText(string(label)) and isSelectable()).performClick()
        settle()
    }

    private fun click(text: String) {
        liftUp(text)
        firstClickable(text).performClick()
    }

    /**
     * Scrolls [text]'s row up its list when it is low on the screen: sorted in another script, the row can
     * come last, under the floating tab bar, which hides it and takes a tap meant for it. The bar floats over
     * the list, so the list counts the row as shown and `performScrollTo` would leave it there.
     */
    private fun liftUp(text: String) {
        val row = firstClickable(text).fetchSemanticsNode()
        val list = generateSequence(row.parent) { it.parent }.firstOrNull { SemanticsActions.ScrollBy in it.config }
        val screen = compose.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().size.height
        if (list != null && row.boundsInRoot.center.y > screen * LOW_ON_SCREEN) {
            val margin = with(compose.density) { ROW_TOP_MARGIN.toPx() }
            compose.onNode(SemanticsMatcher("the row's list") { it.id == list.id })
                .performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                    scroll(0f, row.boundsInRoot.top - list.boundsInRoot.top - margin)
                }
            settle()
        }
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

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun string(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id)

    private data class Shot(
        val name: String,
        val top: Int,
        val bottom: Int,
        val dark: Boolean,
        val marks: Map<String, List<Int>>,
        val texts: Map<String, String>,
    )

    private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

    companion object {
        /**
         * A 1080 × 2400-class phone's 412 × 915 dp, drawn at 4× (1648 px wide): the listing shows the screen
         * about 1450 px wide, so the capture has more pixels than it is shown at and is never stretched.
         */
        const val PHONE = "w412dp-h915dp-xxxhdpi"

        /** Set by `-PstoreScreenshots` (app/build.gradle.kts); where the raw shots are written. */
        const val OUT_PROPERTY = "klas.store.out"
        const val LOCALES_PROPERTY = "klas.store.locales"

        const val TIMEOUT_MS = 60_000L

        /** How long the first screen gets to show «on now» before the app is opened again. */
        const val FIRST_SCREEN_MS = 20_000L
        const val LAUNCH_ATTEMPTS = 3

        /** Room left above a row [scrollToTop] brings up, so a section title is not flush with the bar. */
        val ROW_TOP_MARGIN = 12.dp

        /**
         * How much of the list [scrollToTop] keeps on screen: the tab bar over its foot, and rows above it the
         * video can join the captures before and after at (store/lib/scroll.mjs).
         */
        val KEEP_IN_VIEW = 112.dp

        /** How far down the screen a row may be before [click] scrolls it up: below, the tab bar can cover it. */
        const val LOW_ON_SCREEN = 0.75f
        const val SETTLE_MS = 2_000L

        /**
         * The swipe: where the thumb lands across the row, the mark (`SwipeToConfirm`'s third of the row),
         * how far short of it the first shot stops, how far past it the second goes, and how far each move
         * is — all as fractions of the row's width but the step, in pixels.
         */
        const val SWIPE_FROM = 0.15f
        const val SWIPE_MARK = 0.33f
        const val SHORT_OF_MARK = 0.012f
        const val SWIPE_ARMED = 0.5f
        const val SWIPE_STEP = 4f

        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3_600L
        const val SECONDS_PER_DAY = 86_400L

        /** When the morning agenda is posted, as in the app's default. */
        const val AGENDA_HOUR = 9L

        /** The largest amount a store screenshot may show, times the tutor's [DemoWords.scale]. */
        const val MAX_AMOUNT = 1_000L
        const val DECIMAL = 10

        /** Each language's tutor, by Play code; `-PstoreLocales=de-DE,ja-JP` picks some. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages(): List<Array<Any>> {
            val picked = System.getProperty(LOCALES_PROPERTY)?.split(',')?.map { it.trim() }
            picked?.forEach { code -> require(DemoTutor.all.any { it.play == code }) { "No demo tutor for $code" } }
            return DemoTutor.all.map { it.play }.filter { picked == null || it in picked }.map { arrayOf(it) }
        }
    }
}
