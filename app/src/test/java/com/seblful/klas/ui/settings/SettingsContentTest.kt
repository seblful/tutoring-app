package com.seblful.klas.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private var settings by mutableStateOf(AppSettings())
    private var notificationFixes = 0

    private val actions = NotificationSettingsActions(
        update = { settings = it(settings) },
        reminders = { settings = settings.copy(notificationsEnabled = it) },
        digest = { _, transform -> settings = transform(settings) },
        fixNotifications = { notificationFixes++ },
        fixExactAlarms = { error("Summaries do not need exact alarms") },
        systemNotifications = {},
    )

    @Test
    fun `a digest-only user can repair notifications without enabling lesson reminders`() {
        settings = AppSettings(notificationsEnabled = false, dailyAgendaEnabled = true)
        compose.setContent {
            KlasTheme {
                NotificationSettingsContent(settings, NotificationDelivery.BLOCKED, ReminderDelivery.OFF, actions)
            }
        }

        compose.onNodeWithText("Your phone is blocking notifications").assertExists()
        compose.onNodeWithText("Allow notifications").performClick()
        assertEquals(1, notificationFixes)
        assertFalse(settings.notificationsEnabled)
        assertTrue(settings.dailyAgendaEnabled)
        compose.onNodeWithText("Set up reminders").assertDoesNotExist()
        compose.onNodeWithText("Before lesson").assertIsNotEnabled()
    }

    @Test
    fun `turning off lesson reminders leaves summaries enabled`() {
        settings = AppSettings(dailyAgendaEnabled = true, unfinishedDigestEnabled = true, debtDigestEnabled = true)
        compose.setContent {
            KlasTheme {
                NotificationSettingsContent(settings, NotificationDelivery.ENABLED, ReminderDelivery.ON_TIME, actions)
            }
        }
        compose.onNodeWithText("Lesson reminders").performClick()
        assertFalse(settings.notificationsEnabled)
        assertTrue(settings.dailyAgendaEnabled)
        assertTrue(settings.unfinishedDigestEnabled)
        assertTrue(settings.debtDigestEnabled)
    }

    @Test
    fun `calendar recovery preserves sync and its shared timing updates the notification page`() {
        settings = AppSettings(notificationsEnabled = false, calendarSyncEnabled = true)
        var calendarPage by mutableStateOf(true)
        var calendarFixes = 0
        compose.setContent {
            KlasTheme {
                if (calendarPage) {
                    CalendarSettingsContent(
                        settings, CalendarDelivery.BLOCKED,
                        onSync = { settings = settings.copy(calendarSyncEnabled = it) },
                        onUpdate = actions.update,
                        onFixCalendar = { calendarFixes++ },
                    )
                } else {
                    NotificationSettingsContent(settings, NotificationDelivery.OFF, ReminderDelivery.OFF, actions)
                }
            }
        }
        compose.onNodeWithText("Restore calendar access").performClick()
        assertEquals(1, calendarFixes)
        assertTrue(settings.calendarSyncEnabled)
        compose.onNodeWithText("1 hour before").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("Done").performClick()
        assertEquals(120, settings.reminderLeadMinutes)
        assertFalse(settings.notificationsEnabled)

        compose.runOnIdle { calendarPage = false }
        compose.onNodeWithText("Before lesson").assertIsEnabled()
        compose.onNodeWithText("2 hours before").assertExists()
    }

    @Test
    fun `currency changes require confirmation and cancelling preserves every preference`() {
        settings = AppSettings(currency = Currency.USD, defaultLessonMinutes = 50, defaultGroupPrice = null)
        val before = settings
        compose.setContent {
            KlasTheme {
                LessonSettingsContent(settings, actions.update, onAutoComplete = {})
            }
        }
        chooseCurrency("EUR")
        compose.onNode(hasText("Euro — € (EUR)", substring = true)).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, settings)
        chooseCurrency("EUR")
        compose.onNodeWithText("Change").performClick()
        assertEquals(before.copy(currency = Currency.EUR), settings)
    }

    @Test
    fun `auto-complete is grouped with the lesson settings and the group rate with billing`() {
        compose.setContent {
            KlasTheme { LessonSettingsContent(settings, actions.update, onAutoComplete = {}) }
        }
        val order = listOf(
            "Lessons", "Default lesson duration", "Complete lessons automatically",
            "Billing", "Currency", "Default group hourly rate",
        ).map { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }

        assertEquals(order.sorted(), order)
    }

    private fun chooseCurrency(code: String) {
        compose.onNodeWithText("Currency").performScrollTo().performClick()
        compose.onNode(hasText("($code)", substring = true)).performScrollTo().performClick()
    }

    @Test
    fun `language choices follow autonym order and apply immediately`() {
        compose.setContent {
            KlasTheme { AppearanceSettingsContent(settings, actions.update, showDynamicColor = false) }
        }
        val english = compose.onNodeWithText("English")
        val russian = compose.onNodeWithText("Русский")
        assertTrue(english.fetchSemanticsNode().boundsInRoot.left < russian.fetchSemanticsNode().boundsInRoot.left)
        english.performClick()
        assertEquals(AppLanguage.ENGLISH, settings.language)
    }

    @Test
    fun `index categories open their destinations and blocked delivery takes precedence over counts`() {
        var opened = ""
        compose.setContent {
            KlasTheme {
                SettingsIndex(
                    settings, NotificationDelivery.BLOCKED, ReminderDelivery.BLOCKED, CalendarDelivery.OFF,
                    SettingsNavigation(
                        lessons = { opened = "lessons" },
                        notifications = { opened = "notifications" },
                        calendar = { opened = "calendar" },
                        appearance = { opened = "appearance" },
                        diagnostics = { opened = "diagnostics" },
                    ),
                )
            }
        }
        compose.onNodeWithText("Your phone is blocking notifications").assertExists()
        val categories = mapOf(
            "Lessons & billing" to "lessons",
            "Notifications" to "notifications",
            "Calendar & invitations" to "calendar",
            "Language & appearance" to "appearance",
            "Diagnostics" to "diagnostics",
        )
        for ((label, destination) in categories) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(destination, opened)
        }
        compose.onNodeWithText("Lesson reminders").assertDoesNotExist()
    }
}
