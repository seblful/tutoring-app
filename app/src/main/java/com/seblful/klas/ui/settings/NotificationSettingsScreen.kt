package com.seblful.klas.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun NotificationSettingsScreen(onBack: (() -> Unit)?, viewModel: SettingsViewModel = metroViewModel()) {
    SettingsPage(R.string.settings_notifications_title, onBack, viewModel) { page, modifier, bottomInset, messages ->
        val notify = rememberNotificationAction(viewModel, messages)
        val openExactAlarms = rememberOpenSystemSettings(messages, ::exactAlarmSettings)
        val openNotifications = rememberOpenSystemSettings(messages, ::appNotificationSettings)
        NotificationSettingsContent(
            settings = page.settings,
            notifications = page.notifications,
            reminders = page.reminders,
            actions = NotificationSettingsActions(
                update = viewModel::update,
                reminders = { notify(viewModel.setNotificationsEnabled(it)) },
                digest = { enabled, transform -> notify(viewModel.setDigestEnabled(enabled, transform)) },
                fixNotifications = { notify(viewModel.unblockNotifications()) },
                fixExactAlarms = openExactAlarms,
                systemNotifications = openNotifications,
            ),
            modifier = modifier,
            bottomInset = bottomInset,
        )
    }
}

internal class NotificationSettingsActions(
    val update: ((AppSettings) -> AppSettings) -> Unit,
    val reminders: (Boolean) -> Unit,
    val digest: (Boolean, (AppSettings) -> AppSettings) -> Unit,
    val fixNotifications: () -> Unit,
    val fixExactAlarms: () -> Unit,
    val systemNotifications: () -> Unit,
)

@Composable
internal fun NotificationSettingsContent(
    settings: AppSettings,
    notifications: NotificationDelivery,
    reminders: ReminderDelivery,
    actions: NotificationSettingsActions,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        if (notifications == NotificationDelivery.BLOCKED) {
            SettingsGroup {
                SystemSettingsRow(
                    title = stringResource(R.string.settings_notifications_fix),
                    description = stringResource(R.string.settings_all_notifications_blocked),
                    onClick = actions.fixNotifications,
                )
            }
        }
        SettingsGroup(stringResource(R.string.settings_section_reminders)) {
            LessonReminderSettings(settings, reminders, actions)
        }
        SettingsGroup(stringResource(R.string.settings_section_daily)) { DailySummarySettings(settings, actions) }
        SettingsGroup(stringResource(R.string.settings_section_weekly)) { WeeklySummarySettings(settings, actions) }
        // Untitled: a way out to the system's own page, not a section of this one.
        SettingsGroup {
            SystemSettingsRow(
                title = stringResource(R.string.settings_system_notifications),
                description = stringResource(R.string.settings_system_notifications_desc),
                onClick = actions.systemNotifications,
            )
        }
    }
}

@Composable
private fun LessonReminderSettings(
    settings: AppSettings,
    reminders: ReminderDelivery,
    actions: NotificationSettingsActions,
) {
    SwitchRow(
        title = stringResource(R.string.settings_notifications),
        description = when (reminders) {
            ReminderDelivery.BLOCKED -> stringResource(R.string.settings_notifications_blocked)
            ReminderDelivery.LATE -> stringResource(R.string.settings_notifications_inexact)
            ReminderDelivery.OFF, ReminderDelivery.ON_TIME -> stringResource(R.string.settings_notifications_desc)
        },
        checked = settings.notificationsEnabled,
        onCheckedChange = actions.reminders,
    )
    if (reminders == ReminderDelivery.LATE) {
        SystemSettingsRow(
            title = stringResource(R.string.settings_reminders_fix),
            description = stringResource(R.string.settings_reminders_fix_late),
            onClick = actions.fixExactAlarms,
        )
    }
    ReminderLeadRow(
        minutes = settings.reminderLeadMinutes,
        enabled = settings.canEditReminderLead,
        onSelect = { minutes -> actions.update { it.copy(reminderLeadMinutes = minutes) } },
    )
    SettingsHint(
        stringResource(
            if (settings.canEditReminderLead) R.string.settings_reminder_shared else R.string.settings_reminder_enable,
        ),
    )
}

@Composable
private fun DailySummarySettings(settings: AppSettings, actions: NotificationSettingsActions) {
    SwitchRow(
        title = stringResource(R.string.settings_agenda),
        description = stringResource(R.string.settings_agenda_desc),
        checked = settings.dailyAgendaEnabled,
        onCheckedChange = { on -> actions.digest(on) { it.copy(dailyAgendaEnabled = on) } },
    )
    TimeRow(
        title = stringResource(R.string.settings_agenda_time),
        minuteOfDay = settings.agendaMinuteOfDay,
        enabled = settings.dailyAgendaEnabled,
        description = disabledSummaryHint(settings.dailyAgendaEnabled),
        onSet = { minutes -> actions.update { it.copy(agendaMinuteOfDay = minutes) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_unfinished_digest),
        description = stringResource(
            if (settings.autoCompleteEnabled) {
                R.string.settings_unfinished_autocomplete
            } else {
                R.string.settings_unfinished_digest_desc
            },
        ),
        checked = settings.unfinishedDigestEnabled,
        onCheckedChange = { on -> actions.digest(on) { it.copy(unfinishedDigestEnabled = on) } },
    )
    TimeRow(
        title = stringResource(R.string.settings_unfinished_digest_time),
        minuteOfDay = settings.unfinishedMinuteOfDay,
        enabled = settings.unfinishedDigestEnabled,
        description = disabledSummaryHint(settings.unfinishedDigestEnabled),
        onSet = { minutes -> actions.update { it.copy(unfinishedMinuteOfDay = minutes) } },
    )
}

@Composable
private fun WeeklySummarySettings(settings: AppSettings, actions: NotificationSettingsActions) {
    SwitchRow(
        title = stringResource(R.string.settings_debt_digest),
        description = stringResource(R.string.settings_debt_digest_desc),
        checked = settings.debtDigestEnabled,
        onCheckedChange = { on -> actions.digest(on) { it.copy(debtDigestEnabled = on) } },
    )
    WeekdayRow(
        title = stringResource(R.string.settings_debt_digest_day),
        dayOfWeek = settings.debtDayOfWeek,
        enabled = settings.debtDigestEnabled,
        description = disabledSummaryHint(settings.debtDigestEnabled),
        onSet = { day -> actions.update { it.copy(debtDayOfWeek = day) } },
    )
    TimeRow(
        title = stringResource(R.string.settings_debt_digest_time),
        minuteOfDay = settings.debtMinuteOfDay,
        enabled = settings.debtDigestEnabled,
        onSet = { minutes -> actions.update { it.copy(debtMinuteOfDay = minutes) } },
    )
}

@Composable
private fun disabledSummaryHint(enabled: Boolean): String? =
    if (enabled) null else stringResource(R.string.settings_summary_enable)
