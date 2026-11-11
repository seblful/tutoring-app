package com.seblful.klas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.seblful.klas.domain.time.MINUTES_PER_DAY
import com.seblful.klas.domain.time.MINUTES_PER_HOUR
import com.seblful.klas.text.PluralResource
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ChoiceField
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.designsystem.component.DialogAction
import com.seblful.klas.ui.designsystem.component.FormDialog
import com.seblful.klas.ui.theme.klasSemantics

/**
 * One selected lead time, with presets and a custom amount in minutes, hours or days — the same row on the
 * Notifications and Calendar pages, which share the preference.
 *
 * @param description its second line: that the time is shared, or how to make it editable.
 */
@Composable
fun ReminderLeadRow(minutes: Int, enabled: Boolean, onSelect: (Int) -> Unit, description: String? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    Box {
        ValueRow(stringResource(Strings.settings_reminder_lead), reminderLeadLabel(minutes), enabled, description) {
            expanded = true
        }
        KlasMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            LeadPresets.forEach { preset ->
                TickedMenuItem(
                    text = reminderLeadLabel(preset),
                    selected = minutes == preset,
                    onClick = {
                        expanded = false
                        onSelect(preset)
                    },
                )
            }
            TickedMenuItem(
                text = stringResource(Strings.settings_reminder_lead_custom),
                selected = minutes !in LeadPresets,
                onClick = {
                    expanded = false
                    custom = true
                },
            )
        }
    }
    if (custom && enabled) {
        ReminderLeadPickerModal(
            initialMinutes = minutes,
            onConfirm = {
                custom = false
                onSelect(it)
            },
            onDismiss = { custom = false },
        )
    }
}

/** [ReminderLeadRow]'s second line wherever it is shown: shared with the other page, or why it is off. */
@Composable
fun reminderLeadDescription(enabled: Boolean): String =
    stringResource(if (enabled) Strings.settings_reminder_shared else Strings.settings_reminder_enable)

@Composable
private fun reminderLeadLabel(minutes: Int): String {
    val unit = ReminderUnit.forMinutes(minutes)
    val amount = minutes / unit.minutes
    return pluralStringResource(unit.beforeLabel, amount, amount)
}

@Composable
internal fun ReminderLeadPickerModal(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var unit by rememberSaveable { mutableStateOf(ReminderUnit.forMinutes(initialMinutes)) }
    var amount by rememberSaveable { mutableStateOf((initialMinutes / unit.minutes).toString()) }
    val total = unit.totalMinutes(amount)
    FormDialog(
        title = stringResource(Strings.settings_reminder_lead),
        confirm = DialogAction(
            label = stringResource(Strings.action_done),
            onClick = { total?.let(onConfirm) },
            enabled = total != null,
        ),
        onDismiss = onDismiss,
    ) { ReminderLeadFields(amount, unit, onAmountChange = { amount = it }, onUnitChange = { unit = it }) }
}

@Composable
fun ReminderLeadFields(
    amount: String,
    unit: ReminderUnit,
    onAmountChange: (String) -> Unit,
    onUnitChange: (ReminderUnit) -> Unit,
) {
    val invalid = amount.isNotEmpty() && unit.totalMinutes(amount) == null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { onAmountChange(it.filter(Char::isDigit)) },
                label = { Text(stringResource(Strings.settings_reminder_amount)) },
                isError = invalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.weight(1f)) {
                ChoiceField(
                    label = stringResource(Strings.settings_reminder_unit),
                    selected = unit,
                    options = ReminderUnit.entries,
                    optionLabel = { stringResource(it.label) },
                    onSelect = onUnitChange,
                )
            }
        }
        Text(
            text = if (invalid) {
                stringResource(Strings.settings_reminder_amount_range, unit.maximum)
            } else {
                stringResource(Strings.settings_reminder_day_explanation)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid) klasSemantics.validation else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

enum class ReminderUnit(
    val minutes: Int,
    val label: StringResource,
    val beforeLabel: PluralResource,
) {
    MINUTES(1, Strings.duration_minutes_label, Plurals.settings_reminder_minutes_before),
    HOURS(MINUTES_PER_HOUR, Strings.duration_hours_label, Plurals.settings_reminder_hours_before),
    DAYS(MINUTES_PER_DAY, Strings.settings_reminder_days, Plurals.settings_reminder_days_before),
    ;

    val maximum: Int get() = Int.MAX_VALUE / minutes

    fun totalMinutes(amount: String): Int? = amount.toIntOrNull()?.takeIf { it in 1..maximum }?.times(minutes)

    companion object {
        fun forMinutes(minutes: Int): ReminderUnit = entries.last { minutes % it.minutes == 0 }
    }
}

private val LeadPresets = listOf(30, MINUTES_PER_HOUR, 2 * MINUTES_PER_HOUR, MINUTES_PER_DAY)
