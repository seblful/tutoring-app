package com.seblful.klas.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.seblful.klas.calendar.rememberCalendarPermissionRequest
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun CalendarSettingsScreen(onBack: (() -> Unit)?, viewModel: SettingsViewModel = metroViewModel()) {
    SettingsPage(Strings.settings_calendar_title, onBack, viewModel) { page, modifier, bottomInset, messages ->
        val openSettings = rememberOpenSystemSettings(messages, ::appDetailsSettings)
        val denied = stringResource(Strings.calendar_denied)
        val openLabel = stringResource(Strings.action_open_settings)
        val rationale = stringResource(Strings.calendar_rationale)
        val allowLabel = stringResource(Strings.action_allow)
        val reject = rememberRejectFeedback()
        val requestCalendar = rememberCalendarPermissionRequest {
            if (viewModel.calendarPermissionAnswered() != CalendarSyncToggle.APPLIED) {
                reject()
                messages.show(denied, openLabel, openSettings)
            }
        }
        CalendarSettingsContent(
            settings = page.settings,
            delivery = page.calendar,
            onSync = { on ->
                if (viewModel.setCalendarSyncEnabled(on) == CalendarSyncToggle.PERMISSION_NEEDED) {
                    messages.ask(requestCalendar, rationale, allowLabel)
                }
            },
            onUpdate = viewModel::update,
            onFixCalendar = openSettings,
            modifier = modifier,
            bottomInset = bottomInset,
        )
    }
}

@Composable
internal fun CalendarSettingsContent(
    settings: AppSettings,
    delivery: CalendarDelivery,
    onSync: (Boolean) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onFixCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        SettingsGroup { CalendarSyncRows(settings, delivery, onSync, onUpdate, onFixCalendar) }
        SettingsGroup(stringResource(Strings.settings_reminder_timing)) {
            ReminderLeadRow(
                minutes = settings.reminderLeadMinutes,
                enabled = settings.canEditReminderLead,
                onSelect = { minutes -> onUpdate { it.copy(reminderLeadMinutes = minutes) } },
                description = reminderLeadDescription(settings.canEditReminderLead),
            )
        }
    }
}

@Composable
private fun CalendarSyncRows(
    settings: AppSettings,
    delivery: CalendarDelivery,
    onSync: (Boolean) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onFixCalendar: () -> Unit,
) {
    SwitchRow(
        title = stringResource(Strings.settings_calendar_sync),
        description = stringResource(
            if (delivery == CalendarDelivery.BLOCKED) {
                Strings.settings_calendar_sync_blocked
            } else {
                Strings.settings_calendar_sync_desc
            },
        ),
        checked = settings.calendarSyncEnabled,
        onCheckedChange = onSync,
    )
    if (delivery == CalendarDelivery.BLOCKED) {
        SystemSettingsRow(
            title = stringResource(Strings.settings_calendar_fix),
            description = stringResource(Strings.settings_calendar_fix_desc),
            onClick = onFixCalendar,
        )
    }
    SwitchRow(
        title = stringResource(Strings.settings_email_invites),
        description = stringResource(
            if (settings.calendarSyncEnabled) {
                Strings.settings_email_invites_desc
            } else {
                Strings.settings_email_invites_disabled
            },
        ),
        checked = settings.emailInvitesEnabled,
        enabled = settings.calendarSyncEnabled,
        onCheckedChange = { on -> onUpdate { it.copy(emailInvitesEnabled = on) } },
    )
}
