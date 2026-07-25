package com.seblful.repetit.ui.more

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.ui.common.durationLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsState()
    val context = LocalContext.current
    val deniedMsg = stringResource(R.string.calendar_denied)

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.isNotEmpty() && result.values.all { it }
        if (granted) viewModel.setCalendarSyncEnabled(true)
        else Toast.makeText(context, deniedMsg, Toast.LENGTH_SHORT).show()
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
            // reminders, then calendar sync, class behaviour, money, and look.
            SectionHeader(stringResource(R.string.settings_section_reminders))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                // Quotes the lead time chosen just below, so the two rows always agree.
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_notifications_desc,
                            durationLabel(settings.reminderLeadMinutes),
                        ),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                    )
                },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_reminder_lead),
                options = listOf(30, 60, 120).map { it to durationLabel(it) },
                selected = settings.reminderLeadMinutes,
                onSelect = { viewModel.setReminderLeadMinutes(it) },
            )

            SectionHeader(stringResource(R.string.settings_section_calendar))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_calendar_sync)) },
                supportingContent = { Text(stringResource(R.string.settings_calendar_sync_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.calendarSyncEnabled,
                        onCheckedChange = { on ->
                            if (!on) viewModel.setCalendarSyncEnabled(false)
                            else if (hasCalendarPermission(context)) viewModel.setCalendarSyncEnabled(true)
                            else calendarPermission.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                            )
                        },
                    )
                },
            )

            SectionHeader(stringResource(R.string.settings_section_classes))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_autocomplete)) },
                supportingContent = { Text(stringResource(R.string.settings_autocomplete_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.autoCompleteEnabled,
                        onCheckedChange = { viewModel.setAutoCompleteEnabled(it) },
                    )
                },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_default_lesson),
                options = listOf(45, 60, 90, 120).map { it to durationLabel(it) },
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

private fun hasCalendarPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
