package com.seblful.klas.preview

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.ui.settings.AppearanceSettingsContent
import com.seblful.klas.ui.settings.CalendarDelivery
import com.seblful.klas.ui.settings.CalendarSettingsContent
import com.seblful.klas.ui.settings.LessonSettingsContent
import com.seblful.klas.ui.settings.NotificationDelivery
import com.seblful.klas.ui.settings.NotificationSettingsActions
import com.seblful.klas.ui.settings.NotificationSettingsContent
import com.seblful.klas.ui.settings.ReminderDelivery
import com.seblful.klas.ui.settings.SettingsIndex
import com.seblful.klas.ui.settings.SettingsNavigation

@KlasPreview
@Preview(name = "English", group = "component", showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun SettingsIndexPreview() = PreviewSurface {
    SettingsIndex(
        AppSettings(), NotificationDelivery.BLOCKED, ReminderDelivery.BLOCKED, CalendarDelivery.OFF,
        SettingsNavigation({}, {}, {}, {}, {}),
        Modifier.height(640.dp),
    )
}

@KlasPreview
@Preview(name = "English", group = "component", showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun LessonSettingsPreview() = PreviewSurface {
    LessonSettingsContent(AppSettings(), onUpdate = {}, onAutoComplete = {}, Modifier.height(800.dp))
}

@KlasPreview
@Composable
private fun NotificationSettingsPreview() = PreviewSurface {
    NotificationSettingsContent(
        AppSettings(notificationsEnabled = false, dailyAgendaEnabled = true),
        NotificationDelivery.BLOCKED,
        ReminderDelivery.OFF,
        NotificationSettingsActions({}, {}, { _, _ -> }, {}, {}, {}),
        Modifier.height(800.dp),
    )
}

@KlasPreview
@Composable
private fun CalendarSettingsPreview() = PreviewSurface {
    CalendarSettingsContent(
        AppSettings(notificationsEnabled = false, calendarSyncEnabled = true),
        CalendarDelivery.BLOCKED,
        onSync = {}, onUpdate = {}, onFixCalendar = {},
        Modifier.height(640.dp),
    )
}

@KlasPreview
@Composable
private fun AppearanceSettingsPreview() = PreviewSurface {
    AppearanceSettingsContent(AppSettings(), onUpdate = {}, showDynamicColor = true, Modifier.height(640.dp))
}
