package com.seblful.klas.preview

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.ui.backup.BackupContent
import com.seblful.klas.ui.backup.ConfirmRestoreDialog
import com.seblful.klas.ui.backup.WeeklyBackupViewModel
import com.seblful.klas.ui.common.state.Loadable
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
import kotlin.time.Instant
import com.seblful.klas.data.backup.BackupPreview as FileContents

@KlasPreview
@Preview(name = "English", group = "component", showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun SettingsIndexPreview() = PreviewSurface {
    SettingsIndex(
        AppSettings(), NotificationDelivery.BLOCKED, ReminderDelivery.BLOCKED, CalendarDelivery.OFF,
        SettingsNavigation({}, {}, {}, {}, {}, {}),
        modifier = Modifier.height(640.dp),
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

@KlasPreview
@Preview(name = "English", group = "component", showBackground = true, widthDp = 360, locale = "en")
@Composable
private fun BackupPreview() = PreviewSurface {
    BackupContent(
        lastExportAt = Loadable.Ready(Instant.parse("2026-05-20T10:00:00Z")),
        weekly = Loadable.Ready(WeeklyBackupViewModel.Weekly(folder = "tree", folderName = "Klas", failed = false)),
        importing = false,
        onExport = {},
        onImport = {},
        modifier = Modifier.height(640.dp),
        undoRestoreSince = Instant.parse("2026-06-02T09:00:00Z"),
    )
}

/** What a picked file holds, said before it replaces anything — the longest form, with every kind present. */
@KlasPreview
@Composable
private fun ConfirmRestorePreview() = PreviewSurface {
    ConfirmRestoreDialog(
        preview = FileContents(
            exportedAt = Instant.parse("2026-05-20T10:00:00Z"),
            students = 12,
            groups = 2,
            lessons = 341,
            payments = 118,
            homework = 23,
        ),
        onConfirm = {},
        onDismiss = {},
    )
}

@KlasPreview
@Composable
private fun BackupNeverPreview() = PreviewSurface {
    BackupContent(
        lastExportAt = Loadable.Ready(null),
        weekly = Loadable.Ready(WeeklyBackupViewModel.Weekly(folder = null, folderName = null, failed = false)),
        importing = true,
        onExport = {},
        onImport = {},
        modifier = Modifier.height(640.dp),
    )
}
