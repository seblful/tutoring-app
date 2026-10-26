package com.seblful.klas.baselineprofile

import android.os.SystemClock
import android.widget.EditText
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * The journeys the app's baseline profile is collected from: a cold start; on a fresh install, setup with a
 * subject, a student and their first lesson; then every tab in turn, and the student's card. Run with
 * `./gradlew :app:generateBaselineProfile` on a device of API 33 or later; it writes
 * `app/src/release/generated/baselineProfiles/`, which is committed.
 *
 * Setup is walked rather than skipped so the tabs have rows to draw: an empty app profiles only its empty
 * states, and the first visit to a tab with lessons, students and amounts then runs interpreted.
 *
 * Words, not ids, find the controls: the app draws no test tags for UiAutomator. Each pattern holds the
 * label in both of the app's languages, since it follows the phone's. Tabs are found by their content
 * description: an unmarked tab shows no word.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startAndVisitEveryTab() = rule.collect(
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId is passed by this module's build.gradle.kts"),
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.setUpWithFirstLesson()
        Tabs.forEach { tab ->
            device.clearOverlays()
            device.tap(By.desc(tab))
            device.dismissTip()
            device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            device.waitForIdle()
            if (tab == People) device.openStudentCard()
        }
    }

    /**
     * A fresh install opens on the welcome step: the first subject offered, a student, and their first lesson
     * at the time the form suggests. A later iteration finds the app set up and goes straight on.
     */
    private fun UiDevice.setUpWithFirstLesson() {
        if (!wait(Until.hasObject(By.text(SetUp)), START_TIMEOUT_MS)) return
        advance(By.text(SetUp), "the language step") { hasObject(By.text(Next)) }
        advance(By.text(Next), "the subjects step") { hasObject(By.text(FirstSubject)) }
        // By name: the language step's segments are checkable too.
        val added = By.checked(true).hasDescendant(By.text(FirstSubject))
        advance(By.text(FirstSubject), "the subject added") { hasObject(added) }
        // The step's two fields, the name above the rate: the rate's shows once the subject has been read back.
        val fields = { findObjects(By.clazz(EditText::class.java)) }
        advance(By.text(Next), "a name and a rate to fill in") { fields().size >= 2 }
        fields()[0].text = STUDENT
        fields()[1].text = "20"
        advance(By.text(Done), "the student added") { hasObject(By.text(BookFirst)) }
        advance(By.text(BookFirst), "the booking form") { hasObject(By.text(ChooseDay)) }
        advance(By.text(ChooseDay), "the date picker") { hasObject(By.text(Done)) }
        advance(By.text(Done), "a day chosen") { !hasObject(By.text(Done)) }
        advance(By.text(SetTime), "the time picker") { hasObject(By.text(Done)) }
        advance(By.text(Done), "a time chosen") { !hasObject(By.text(Done)) }
        // Saved, the form closes: the tab bar shows, or the reminders prompt over it.
        advance(By.text(Book), "the lesson booked") { hasObject(By.desc(Tabs.last())) || hasObject(PermissionPrompt) }
    }

    private fun UiDevice.openStudentCard() {
        tap(By.text(STUDENT))
        dismissTip()
        findObject(By.scrollable(true))?.fling(Direction.DOWN)
        waitForIdle()
        pressBack()
        waitForIdle()
    }

    /**
     * Puts away what can cover the tab bar, until it shows: the reminders permission prompt, which follows a
     * booking a moment later — the profile is about the app, not the system's dialog — and a tip.
     */
    private fun UiDevice.clearOverlays() {
        val deadline = SystemClock.uptimeMillis() + START_TIMEOUT_MS
        while (!hasObject(By.desc(Tabs.last())) && SystemClock.uptimeMillis() < deadline) {
            findObject(PermissionPrompt)?.click()
            findObject(By.text(GotIt))?.click()
            SystemClock.sleep(RETAP_MS)
        }
    }

    /** A screen's first visit can lay a tip over it, a moment after it opens. */
    private fun UiDevice.dismissTip() {
        wait(Until.findObject(By.text(GotIt)), TIP_TIMEOUT_MS)?.click()
        waitForIdle()
    }

    /**
     * Taps [selector] once it shows, **or fails the run**: a control the journey cannot find means the app has
     * changed under it, and a profile collected past it silently covers less.
     */
    private fun UiDevice.tap(selector: BySelector) {
        val target = checkNotNull(wait(Until.findObject(selector), STEP_TIMEOUT_MS)) { "Nothing matches $selector" }
        target.click()
        waitForIdle()
    }

    /**
     * Taps [target] until [arrived] — **or fails the run**, naming [where] it meant to get. A tap while a step
     * slides in, or before a form has taken a typed value, is lost; one that never lands means the app has changed
     * under the journey, and a profile collected past it silently covers less.
     */
    private fun UiDevice.advance(target: BySelector, where: String, arrived: UiDevice.() -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + STEP_TIMEOUT_MS
        while (!arrived()) {
            check(SystemClock.uptimeMillis() < deadline) { "Never reached $where" }
            findObject(target)?.click()
            SystemClock.sleep(RETAP_MS)
        }
        waitForIdle()
    }

    private companion object {
        const val START_TIMEOUT_MS = 5_000L
        const val STEP_TIMEOUT_MS = 5_000L
        const val RETAP_MS = 700L
        const val TIP_TIMEOUT_MS = 1_000L
        const val STUDENT = "Anna"

        val SetUp: Pattern = Pattern.compile("Set up|Настроить")
        val Next: Pattern = Pattern.compile("Next|Далее")
        val FirstSubject: Pattern = Pattern.compile("Math|Математика")
        val PermissionPrompt: BySelector = By.res("com.android.permissioncontroller", "permission_deny_button")
        val GotIt: Pattern = Pattern.compile("Got it|Понятно")
        val Done: Pattern = Pattern.compile("Done|Готово")
        val BookFirst: Pattern = Pattern.compile("Book the first lesson|Записать первое занятие")
        val ChooseDay: Pattern = Pattern.compile("Choose a day|Выбрать день")
        val SetTime: Pattern = Pattern.compile("Set time|Выбрать время")
        val Book: Pattern = Pattern.compile("Book|Записать")
        val People: Pattern = Pattern.compile("People|Люди")

        /** The tab bar's names, Today — the tab the app opens on — last, so its list is the one scrolled. */
        val Tabs: List<Pattern> = listOf(
            Pattern.compile("Schedule|Расписание"),
            People,
            Pattern.compile("Homework|Задания"),
            Pattern.compile("Reports|Отчёты"),
            Pattern.compile("Today|Сегодня"),
        )
    }
}
