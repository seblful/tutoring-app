package com.seblful.klas.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * The journeys the app's baseline profile is collected from: a cold start, past first run on a fresh install,
 * then every tab in turn. Run with `./gradlew :app:generateBaselineProfile` on a device of API 33 or later; it
 * writes `app/src/release/generated/baselineProfiles/`, which is committed.
 *
 * Words, not ids, find the controls: the app draws no test tags for UiAutomator. Each pattern holds the
 * label in both of the app's languages, since it follows the phone's.
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
        device.skipFirstRun()
        Tabs.forEach { tab ->
            device.declinePermission()
            device.findObject(By.text(tab))?.click() ?: return@forEach
            device.waitForIdle()
            device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }

    /** A fresh install opens on the welcome step; «Пропустить» goes straight to the app. */
    private fun UiDevice.skipFirstRun() {
        wait(Until.findObject(By.text(Skip)), START_TIMEOUT_MS)?.click() ?: return
        wait(Until.findObject(By.text(Tabs.first())), START_TIMEOUT_MS)
    }

    /** The schedule asks to send reminders; the profile is about the app, not the system's dialog. */
    private fun UiDevice.declinePermission() {
        findObject(By.res("com.android.permissioncontroller", "permission_deny_button"))?.click()
    }

    private companion object {
        const val START_TIMEOUT_MS = 5_000L
        val Skip: Pattern = Pattern.compile("Skip|Пропустить")

        /** The tab bar's labels, Schedule — the tab the app opens on — last, so its list is the one scrolled. */
        val Tabs: List<Pattern> = listOf(
            "People|Люди",
            "Homework|Задания",
            "Reports|Отчёты",
            "More|Ещё",
            "Schedule|Расписание",
        ).map(Pattern::compile)
    }
}
