package com.seblful.klas.ui.more

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.seblful.klas.R
import com.seblful.klas.ui.common.ChoiceField
import com.seblful.klas.ui.common.DisabledAlpha
import com.seblful.klas.ui.common.KeepTypedInput
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.theme.KlasIcons

/** One selected lead time, with presets and a custom amount in minutes, hours or days. */
@Composable
internal fun ReminderLeadRow(minutes: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_reminder_lead)) },
            supportingContent = { Text(reminderLeadLabel(minutes)) },
            trailingContent = { Icon(KlasIcons.Filled.ArrowDropDown, contentDescription = null) },
            colors = if (enabled) {
                ListItemDefaults.colors()
            } else {
                ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
                    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
                )
            },
            modifier = Modifier.insetRowPress {
                Modifier.clickable(it, indication = null, enabled = enabled, onClick = { expanded = true })
            },
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
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
                text = stringResource(R.string.settings_reminder_lead_custom),
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
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = KeepTypedInput,
        title = { Text(stringResource(R.string.settings_reminder_lead)) },
        text = { ReminderLeadFields(amount, unit, onAmountChange = { amount = it }, onUnitChange = { unit = it }) },
        confirmButton = {
            TextButton(onClick = { total?.let(onConfirm) }, enabled = total != null) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
internal fun ReminderLeadFields(
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
                label = { Text(stringResource(R.string.settings_reminder_amount)) },
                isError = invalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.weight(1f)) {
                ChoiceField(
                    label = stringResource(R.string.settings_reminder_unit),
                    selected = unit,
                    options = ReminderUnit.entries,
                    optionLabel = { stringResource(it.label) },
                    onSelect = onUnitChange,
                )
            }
        }
        Text(
            text = if (invalid) {
                stringResource(R.string.settings_reminder_amount_range, unit.maximum)
            } else {
                stringResource(R.string.settings_reminder_day_explanation)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal enum class ReminderUnit(
    val minutes: Int,
    @param:StringRes val label: Int,
    @param:PluralsRes val beforeLabel: Int,
) {
    MINUTES(1, R.string.duration_minutes_label, R.plurals.settings_reminder_minutes_before),
    HOURS(MinutesPerHour, R.string.duration_hours_label, R.plurals.settings_reminder_hours_before),
    DAYS(MinutesPerDay, R.string.settings_reminder_days, R.plurals.settings_reminder_days_before),
    ;

    val maximum: Int get() = Int.MAX_VALUE / minutes

    fun totalMinutes(amount: String): Int? = amount.toIntOrNull()?.takeIf { it in 1..maximum }?.times(minutes)

    companion object {
        fun forMinutes(minutes: Int): ReminderUnit = entries.last { minutes % it.minutes == 0 }
    }
}

private val LeadPresets = listOf(30, 60, 120, 24 * 60)

private const val MinutesPerHour = 60
private const val MinutesPerDay = 24 * MinutesPerHour
