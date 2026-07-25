package com.seblful.repetit.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.format.MoneyField
import com.seblful.repetit.format.toAmountInput
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.ui.common.text

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
                onCheckedChange = { on -> viewModel.update { it.copy(notificationsEnabled = on) } },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_reminder_lead),
                options = listOf(
                    30 to stringResource(R.string.minutes_short, 30),
                    60 to stringResource(R.string.hours_one),
                    120 to stringResource(R.string.hours_two),
                ),
                selected = settings.reminderLeadMinutes,
                onSelect = { minutes -> viewModel.update { it.copy(reminderLeadMinutes = minutes) } },
            )

            SectionHeader(stringResource(R.string.settings_section_calendar))
            SwitchRow(
                title = stringResource(R.string.settings_calendar_sync),
                description = stringResource(R.string.settings_calendar_sync_desc),
                checked = settings.calendarSyncEnabled,
                onCheckedChange = { on ->
                    if (viewModel.setCalendarSyncEnabled(on) == CalendarSyncToggle.PERMISSION_NEEDED) {
                        calendarPermission.launch(viewModel.calendarPermissionsRequired)
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
                onCheckedChange = { on -> viewModel.update { it.copy(autoCompleteEnabled = on) } },
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
                onSelect = { minutes -> viewModel.update { it.copy(defaultLessonMinutes = minutes) } },
            )

            SectionHeader(stringResource(R.string.settings_section_money))
            ChoiceRow(
                title = stringResource(R.string.settings_currency),
                options = listOf(
                    "BYN" to stringResource(R.string.currency_byn),
                    "RUB" to stringResource(R.string.currency_rub),
                ),
                selected = settings.currency,
                onSelect = { code -> viewModel.update { it.copy(currency = code) } },
            )
            // Free text rather than a segmented control: unlike a lesson length or a currency, there
            // is no shortlist of plausible amounts to offer.
            MoneyRow(
                title = stringResource(R.string.settings_default_group_price),
                description = stringResource(R.string.settings_default_group_price_desc),
                minor = settings.defaultGroupPriceMinor,
                onSet = { minor -> viewModel.update { it.copy(defaultGroupPriceMinor = minor) } },
            )

            SectionHeader(stringResource(R.string.settings_section_menu))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_groups_tab)) },
                supportingContent = { Text(stringResource(R.string.settings_groups_tab_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.showGroupsTab,
                        onCheckedChange = { on -> viewModel.update { it.copy(showGroupsTab = on) } },
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
                onSelect = { mode -> viewModel.update { it.copy(themeMode = mode) } },
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

/**
 * A titled row holding one money amount, edited in a dialog. Shows the amount itself as the value, so
 * the row reads as a statement of the setting rather than as a link to go and look.
 *
 * **Clearing the field unsets it** (0, «Не задана»), which is why the dialog's save stays enabled on
 * an empty field: one control both sets and removes the default, instead of an amount the tutor can
 * never take back once typed.
 */
@Composable
private fun MoneyRow(
    title: String,
    description: String,
    minor: Long,
    onSet: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Text(
                if (minor == 0L) stringResource(R.string.settings_not_set) else Money(minor).text(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.clickable { editing = true },
    )
    if (editing) {
        // Opens on the amount currently set, so editing it is a correction rather than a re-entry.
        // OPTIONAL_RATE is what makes clearing the box mean "not set" rather than being invalid.
        var field by remember {
            mutableStateOf(
                MoneyField(
                    text = if (minor == 0L) "" else Money(minor).toAmountInput(),
                    kind = MoneyField.Kind.OPTIONAL_RATE,
                ),
            )
        }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = field.text,
                    onValueChange = { field = field.typed(it) },
                    label = { Text(stringResource(R.string.price_label)) },
                    isError = field.isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        field.amount?.let { onSet(it.minor) }
                        editing = false
                    },
                    enabled = field.isValid,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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
