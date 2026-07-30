package com.seblful.klas.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.toAmountInput
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.currencyLabel
import com.seblful.klas.ui.common.moneyErrorText
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.supportingTextOf
import com.seblful.klas.ui.common.text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsState()
    val notificationsPermitted by viewModel.notificationsPermitted.collectAsState()
    val pendingSweep by viewModel.pendingSweep.collectAsState()
    val messages = rememberMessages()
    val deniedMsg = stringResource(R.string.calendar_denied)
    val notificationsBlockedMsg = stringResource(R.string.settings_notifications_blocked)

    // The count comes with the event, so the plural is resolved from the context rather than read
    // above the collector as the other screens do — `stringResource` cannot be called from inside one,
    // and there is no state here to pre-resolve it from. The app's resources are pinned to Russian
    // (`inRussian`), which is what makes the quantity rule the right one.
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel, messages) {
        viewModel.swept.stream.collect { count ->
            messages.show(resources.getQuantityString(R.plurals.settings_autocomplete_swept, count, count))
        }
    }

    // A permission can be granted or revoked in system settings while the app is away, so the screen
    // re-asks each time it comes back rather than trusting what it learned when it opened.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermissions()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    // Requesting the permission is all this screen does about it — whether sync may be enabled is
    // the view model's rule, so asking again after a grant simply goes back through it.
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.isNotEmpty() && result.values.all { it }
        val applied = granted && viewModel.setCalendarSyncEnabled(true) == CalendarSyncToggle.APPLIED
        if (!applied) messages.show(deniedMsg)
    }

    // The currency the tutor picked but has not yet confirmed — null while nothing is being asked.
    // Held rather than applied, because switching it re-labels every amount already recorded.
    var pendingCurrency by remember { mutableStateOf<Currency?>(null) }

    KlasScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        messages = messages,
    ) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {
            // Grouped by what each setting governs, so a glance tells you where to look:
            // reminders, then calendar sync, class behaviour, money, the menu, and look.
            SectionHeader(stringResource(R.string.settings_section_reminders))
            SwitchRow(
                title = stringResource(R.string.settings_notifications),
                // The row says what the platform will actually do, not only what the tutor asked for.
                // With the setting on and the permission missing, no reminder can ever appear — which
                // used to be true in complete silence, on every fresh install.
                description = if (settings.notificationsEnabled && !notificationsPermitted) {
                    stringResource(R.string.settings_notifications_blocked)
                } else {
                    stringResource(R.string.settings_notifications_desc)
                },
                checked = settings.notificationsEnabled,
                onCheckedChange = { on ->
                    viewModel.update { it.copy(notificationsEnabled = on) }
                    // Asked here, where the tutor has just said they want reminders — rather than over
                    // the first screen of a first launch, before they had met the feature. Which of the
                    // three things to do is [NotificationsIntent]'s.
                    when (val intent = viewModel.notificationsIntent(on)) {
                        is NotificationsIntent.Request -> notificationPermission.launch(intent.permissions)
                        NotificationsIntent.BlockedBySystem -> messages.show(notificationsBlockedMsg)
                        NotificationsIntent.Nothing -> Unit
                    }
                },
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
                onCheckedChange = { on -> viewModel.update { it.copy(emailInvitesEnabled = on) } },
            )

            SectionHeader(stringResource(R.string.settings_section_classes))
            SwitchRow(
                title = stringResource(R.string.settings_autocomplete),
                description = stringResource(R.string.settings_autocomplete_desc),
                checked = settings.autoCompleteEnabled,
                // Not the generic `update`: turning this on settles — and bills — every lesson that has
                // already finished, so whether that needs asking about first is the view model's to
                // decide. It answers through [SettingsViewModel.pendingSweep], below.
                onCheckedChange = { on -> viewModel.setAutoCompleteEnabled(on) },
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
            // Asks first, because the change is a **re-labelling and not a conversion**: money is
            // stored as minor units and the code is only what gets printed beside them, so 100 BYN of
            // recorded history begins reading as 100 RUB. That is the right behaviour for an offline
            // app — inventing an exchange rate would be worse, and rewriting billed history worse
            // still — but it is not what "сменить валюту" leads a tutor to expect, so it is said out
            // loud before every stored amount changes meaning.
            ChoiceRow(
                title = stringResource(R.string.settings_currency),
                // The legal set is [Currency]'s, not this screen's — the row only words it.
                options = Currency.entries.map { it.name to currencyLabel(it) },
                selected = settings.currency.name,
                onSelect = { code ->
                    val picked = Currency.parse(code)
                    if (picked != settings.currency) pendingCurrency = picked
                },
            )
            // Free text rather than a segmented control: unlike a lesson length or a currency, there
            // is no shortlist of plausible amounts to offer.
            MoneyRow(
                title = stringResource(R.string.settings_default_group_price),
                description = stringResource(R.string.settings_default_group_price_desc),
                amount = settings.defaultGroupPrice,
                onSet = { price -> viewModel.update { it.copy(defaultGroupPrice = price) } },
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

            // Last, and deliberately dull. Diagnostics used to stand in «Ещё» beside «Настройки» and
            // «Резервная копия», which put a crash-log viewer at the same level as the things a tutor
            // uses. It belongs where service tools belong: at the bottom of settings.
            SectionHeader(stringResource(R.string.settings_section_service))
            ListItem(
                headlineContent = { Text(stringResource(R.string.diagnostics_title)) },
                supportingContent = { Text(stringResource(R.string.diagnostics_desc)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, null) },
                modifier = Modifier.clickable(onClick = onDiagnostics),
            )
        }
    }

    pendingCurrency?.let { code ->
        ConfirmDialog(
            text = stringResource(R.string.settings_currency_change_confirm, code.name),
            confirmLabel = stringResource(R.string.settings_currency_change),
            onConfirm = { viewModel.update { it.copy(currency = code) } },
            onDismiss = { pendingCurrency = null },
        )
    }
    // Held in the view model rather than here, unlike the currency above: what the question is *about*
    // — how many finished lessons are waiting — has to be counted before it can be asked.
    pendingSweep?.let { backlog ->
        ConfirmDialog(
            text = pluralStringResource(
                R.plurals.settings_autocomplete_sweep_confirm,
                backlog,
                backlog,
            ),
            confirmLabel = stringResource(R.string.action_enable),
            onConfirm = { viewModel.confirmPendingSweep() },
            onDismiss = { viewModel.dismissPendingSweep() },
        )
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
 * **Clearing the field unsets it** (null, «Не задана»), which is why the dialog's save stays enabled on
 * an empty field: one control both sets and removes the default, instead of an amount the tutor can
 * never take back once typed.
 */
@Composable
private fun MoneyRow(
    title: String,
    description: String,
    amount: Money?,
    onSet: (Money?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Text(
                amount?.text() ?: stringResource(R.string.settings_not_set),
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
                    text = amount?.toAmountInput() ?: "",
                    kind = MoneyField.Kind.OPTIONAL_RATE,
                ),
            )
        }
        AlertDialog(
            onDismissRequest = { editing = false },
            // The rule every dialog the tutor types into follows, and the one place that was missing
            // it: a stray tap beside the box must not throw away the amount just entered. See
            // `KeepTypedInput` in ui/common/Components.kt for the reasoning; «Отмена» is still there
            // for leaving on purpose, and back still closes it.
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = field.text,
                    onValueChange = { field = field.typed(it) },
                    label = { Text(stringResource(R.string.price_label)) },
                    isError = field.isError,
                    supportingText = supportingTextOf(moneyErrorText(field)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // A blank OPTIONAL_RATE parses as zero, and zero is "not set".
                        onSet(field.amount?.takeUnless { it.isZero })
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.sectionGap, bottom = Spacing.tight),
    )
}

/**
 * A labelled row with a single-choice segmented control.
 *
 * Through [SegmentedChoice], so these four rows get the one-line shrink rule the schedule's segments
 * already had — «Как в системе» in a third of a phone is exactly the label that needed it, and it was
 * wrapping here while the identical control two screens away did not.
 */
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.inline)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SegmentedChoice(
            options = options.map { it.first },
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.padding(top = Spacing.inline),
            label = { value -> options.first { it.first == value }.second },
        )
    }
}
