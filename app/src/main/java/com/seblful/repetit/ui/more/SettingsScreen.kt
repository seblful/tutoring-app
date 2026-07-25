package com.seblful.repetit.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.calendar.AndroidCalendarPermission
import com.seblful.repetit.ui.common.constrainedContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsState()
    val context = LocalContext.current
    val deniedMsg = stringResource(R.string.calendar_denied)

    // Requesting the permission is all this screen does about it — whether sync may be enabled is
    // the view model's rule, so asking again after a grant simply goes back through it.
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.isNotEmpty() && result.values.all { it }
        val applied = granted && viewModel.setCalendarSyncEnabled(true) == CalendarSyncToggle.APPLIED
        if (!applied) Toast.makeText(context, deniedMsg, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).constrainedContent().verticalScroll(rememberScrollState()),
        ) {
            // Grouped by what each setting governs, so a glance tells you where to look:
            // reminders, then calendar sync, class behaviour, money, the menu, and look.
            SectionHeader(stringResource(R.string.settings_section_reminders))
            SwitchRow(
                title = stringResource(R.string.settings_notifications),
                description = stringResource(R.string.settings_notifications_desc),
                checked = settings.notificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_reminder_lead),
                options = listOf(
                    30 to stringResource(R.string.minutes_short, 30),
                    60 to stringResource(R.string.hours_one),
                    120 to stringResource(R.string.hours_two),
                ),
                selected = settings.reminderLeadMinutes,
                onSelect = { viewModel.setReminderLeadMinutes(it) },
            )

            SectionHeader(stringResource(R.string.settings_section_calendar))
            SwitchRow(
                title = stringResource(R.string.settings_calendar_sync),
                description = stringResource(R.string.settings_calendar_sync_desc),
                checked = settings.calendarSyncEnabled,
                onCheckedChange = { on ->
                    if (viewModel.setCalendarSyncEnabled(on) == CalendarSyncToggle.PERMISSION_NEEDED) {
                        calendarPermission.launch(AndroidCalendarPermission.REQUIRED)
                    }
                },
            )
            SwitchRow(
                title = stringResource(R.string.settings_email_invites),
                description = stringResource(R.string.settings_email_invites_desc),
                checked = settings.emailInvitesEnabled,
                // The invitation rides on the class's calendar event, so this switch can only do
                // anything while sync is on — shown dimmed rather than hidden, so the tutor can see
                // that syncing alone mails nobody, and where to opt in if they want it to.
                enabled = settings.calendarSyncEnabled,
                onCheckedChange = { viewModel.setEmailInvitesEnabled(it) },
            )

            SectionHeader(stringResource(R.string.settings_section_classes))
            SwitchRow(
                title = stringResource(R.string.settings_autocomplete),
                description = stringResource(R.string.settings_autocomplete_desc),
                checked = settings.autoCompleteEnabled,
                onCheckedChange = { viewModel.setAutoCompleteEnabled(it) },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_default_lesson),
                options = listOf(
                    45 to stringResource(R.string.minutes_short, 45),
                    60 to stringResource(R.string.hours_one),
                    90 to stringResource(R.string.hours_one_and_half),
                    120 to stringResource(R.string.hours_two),
                ),
                selected = settings.defaultLessonMinutes,
                onSelect = { viewModel.setDefaultLessonMinutes(it) },
            )

            SectionHeader(stringResource(R.string.settings_section_money))
            ChoiceRow(
                title = stringResource(R.string.settings_currency),
                options = listOf(
                    "BYN" to stringResource(R.string.currency_byn),
                    "RUB" to stringResource(R.string.currency_rub),
                ),
                selected = settings.currency,
                onSelect = { viewModel.setCurrency(it) },
            )

            SectionHeader(stringResource(R.string.settings_section_menu))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_groups_tab)) },
                supportingContent = { Text(stringResource(R.string.settings_groups_tab_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.showGroupsTab,
                        onCheckedChange = { viewModel.setShowGroupsTab(it) },
                    )
                },
            )

            SectionHeader(stringResource(R.string.settings_section_appearance))
            ChoiceRow(
                title = stringResource(R.string.settings_theme),
                options = listOf(
                    "SYSTEM" to stringResource(R.string.theme_system),
                    "LIGHT" to stringResource(R.string.theme_light),
                    "DARK" to stringResource(R.string.theme_dark),
                ),
                selected = settings.themeMode,
                onSelect = { viewModel.setThemeMode(it) },
            )
        }
    }
}

/**
 * A titled row with a trailing switch. When [enabled] is false the switch is inert and the labels
 * are dimmed, so a setting that depends on another one reads as unavailable rather than as off.
 */
@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = if (enabled) {
            ListItemDefaults.colors()
        } else {
            ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            )
        },
    )
}

/** Material's standard opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/** A small, tinted section label that breaks the settings list into scannable groups. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** A labelled row with a single-choice segmented control. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
    }
}
