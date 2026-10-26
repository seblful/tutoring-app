package com.seblful.klas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.toAmountInput
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.DialogRadioRow
import com.seblful.klas.ui.common.DisabledAlpha
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.rememberRevealedFocus
import com.seblful.klas.ui.common.rememberToggleFeedback
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.ChoiceDialog
import com.seblful.klas.ui.designsystem.component.DialogAction
import com.seblful.klas.ui.designsystem.component.FormDialog
import com.seblful.klas.ui.designsystem.component.settingsRowColors
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * The default lesson length as a few presets and a custom value.
 *
 * @param customWord the way out's name while a preset is in force.
 */
@Composable
internal fun DurationPresetRow(
    title: String,
    presets: List<Int>,
    minutes: Int,
    customWord: String,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    ChoiceRow(
        title = title,
        options = presets.map { it to durationLabel(it) } +
            (CustomMinutes to if (minutes in presets) customWord else durationLabel(minutes)),
        selected = minutes.takeIf { it in presets } ?: CustomMinutes,
        onSelect = { picked -> if (picked == CustomMinutes) onCustom() else onSelect(picked) },
        onReselect = { picked -> if (picked == CustomMinutes) onCustom() },
    )
}

internal val LessonPresets = listOf(45, 60, 90)

private const val CustomMinutes = -1

internal val SettingsRowHeight = 72.dp

@Composable
internal fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val toggleFeedback = rememberToggleFeedback()
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        // The switch is a picture of the state; the **row** is the control, so a tap on the words works
        // and TalkBack reads the title with it as one switch rather than «Switch, on» on its own.
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.insetRowPress { source ->
            Modifier.toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { on ->
                toggleFeedback(on)
                onCheckedChange(on)
            }
        },
        colors = settingsRowColors(enabled),
    )
}

@Composable
internal fun TimeRow(
    title: String,
    minuteOfDay: Int,
    enabled: Boolean,
    onSet: (Int) -> Unit,
    description: String? = null,
) {
    var picking by remember { mutableStateOf(false) }
    val time = Digests.timeOf(minuteOfDay)
    ValueRow(title = title, value = time.timeText(), enabled = enabled, description = description) { picking = true }
    if (picking) {
        TimePickerModal(
            initial = time,
            onConfirm = {
                onSet(it.hour * 60 + it.minute)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
internal fun WeekdayRow(
    title: String,
    dayOfWeek: Int,
    enabled: Boolean,
    onSet: (Int) -> Unit,
    description: String? = null,
) {
    var picking by remember { mutableStateOf(false) }
    val names = stringArrayResource(R.array.weekday_names)
    val selected = Digests.dayOfWeekOf(dayOfWeek)

    ValueRow(title = title, value = names[selected.isoDayNumber - 1], enabled = enabled, description = description) {
        picking = true
    }
    if (picking) {
        ChoiceDialog(title = title, onDismiss = { picking = false }) {
            Column {
                DayOfWeek.entries.forEach { day ->
                    DialogRadioRow(
                        label = names[day.isoDayNumber - 1],
                        selected = day == selected,
                        onClick = {
                            onSet(day.isoDayNumber)
                            picking = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ValueRow(
    title: String,
    value: String,
    enabled: Boolean,
    description: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { text -> { Text(text) } },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
                },
            )
        },
        // The description stays legible while disabled: it is what says how to enable the row.
        colors = if (enabled) {
            settingsRowColors()
        } else {
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
            )
        },
        modifier = Modifier.insetRowPress {
            Modifier.clickable(it, indication = null, enabled = enabled, onClick = onClick)
        },
    )
}

@Composable
internal fun SystemSettingsRow(title: String, description: String, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.action_open_settings)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Icon(KlasIcons.Filled.OpenInNew, contentDescription = null) },
        colors = settingsRowColors(),
        modifier = Modifier.insetRowPress {
            Modifier.clickable(it, indication = null, onClickLabel = openLabel, onClick = onClick)
        },
    )
}

@Composable
internal fun SettingsHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
    )
}

@Composable
internal fun MoneyRow(
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = settingsRowColors(),
        modifier = Modifier.insetRowPress { Modifier.clickable(it, indication = null) { editing = true } },
    )
    if (editing) {
        // Opens on the amount currently set, so editing it is a correction rather than a re-entry.
        // OPTIONAL_RATE is what makes clearing the box mean "not set" rather than being invalid. The text is
        // saveable and the field derived from it, as every typed dialog field is.
        var text by rememberSaveable { mutableStateOf(amount?.toAmountInput().orEmpty()) }
        val field = MoneyField(text, MoneyField.Kind.OPTIONAL_RATE)
        FormDialog(
            title = title,
            confirm = DialogAction(
                label = stringResource(R.string.action_save),
                onClick = {
                    // A blank OPTIONAL_RATE parses as zero, and zero is "not set".
                    onSet(field.amount?.takeUnless { it.isZero })
                    editing = false
                },
                enabled = field.isValid,
            ),
            onDismiss = { editing = false },
        ) {
            MoneyTextField(
                field = field,
                onValueChange = { text = it },
                label = stringResource(R.string.price_label),
                // Like every other one-field dialog: the caret already in it, since a price is the only
                // thing it is opened to do.
                modifier = Modifier.focusRequester(rememberRevealedFocus()),
            )
        }
    }
}
