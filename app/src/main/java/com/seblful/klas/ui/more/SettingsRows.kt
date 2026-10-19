package com.seblful.klas.ui.more

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
import com.seblful.klas.ui.common.KeepTypedInput
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.rememberRevealedFocus
import com.seblful.klas.ui.common.rememberToggleFeedback
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/** A row that hands over to the phone's own settings. Decorative, like [OpensScreenMark]. */
@Composable
internal fun LeavesAppMark() {
    Icon(KlasIcons.Filled.OpenInNew, contentDescription = null)
}

/**
 * A duration setting as **a few presets and a way out of them** — the reminder lead and the lesson length.
 *
 * The last option is not a *value*: it opens the same hours-and-minutes picker the booking form uses for
 * «Другая», so any length is expressible rather than only the few somebody guessed at. While the stored
 * one **is** custom, that segment is labelled with it and reads as selected — the row must show the setting
 * in force, not «Другое» over an answer it is hiding — and a second custom one is asked for by tapping it
 * again. Three presets and the way out stay inside `MaxSegments`, so both rows draw as segments rather than
 * one of them falling back to a dropdown.
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

/** The reminder leads offered as one tap. Anything else is reachable through [CustomMinutes]. */
internal val LeadPresets = listOf(30, 60, 120)

/** The lesson lengths offered as one tap. Anything else is reachable through [CustomMinutes]. */
internal val LessonPresets = listOf(45, 60, 90)

/**
 * The «Другое» option of both duration rows — an *act*, not a length.
 *
 * Negative so it can never collide with a real one: a lead is a positive number of minutes, and
 * `DurationPickerModal` refuses zero.
 */
private const val CustomMinutes = -1

internal val SettingsRowHeight = 72.dp

/**
 * A titled row with a trailing switch. When [enabled] is false the switch is inert and the labels
 * are dimmed, so a setting that depends on another one reads as unavailable rather than as off.
 *
 * **The flip is felt, here rather than at the six call sites.** A stored fact changing is what earns a
 * haptic, and every switch in this screen stores one; wiring it per row would mean six chances to forget
 * and six chances to buzz "on" while turning off.
 *
 * It says which way it went and **not** whether it stuck, because those are different facts and only one of
 * them is known at this moment: two of these switches can be *refused* — calendar sync without its
 * permission — and the refusal is reported by the row's own callback, which is where
 * [rememberRejectFeedback] belongs. A toggle that buzzed only on success would leave the tutor's thumb
 * waiting on a control that had already sprung back.
 */
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
        colors = if (enabled) {
            ListItemDefaults.colors()
        } else {
            ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha),
            )
        },
    )
}

/**
 * A row holding one **time of day**, stored as minutes past local midnight and edited in the app's own
 * time picker.
 *
 * The value is printed through [timeText], so it is 12- or 24-hour by the phone's clock and matches the
 * dial that sets it — the rule `ClockFormatTest` pins, and the reason no screen formats a time itself.
 * [Digests.timeOf] is what turns the stored integer back into a wall clock, including the clamp that keeps
 * an impossible stored value from throwing on a background path.
 */
@Composable
internal fun TimeRow(
    title: String,
    minuteOfDay: Int,
    enabled: Boolean,
    onSet: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val time = Digests.timeOf(minuteOfDay)
    ValueRow(title = title, value = time.timeText(), enabled = enabled) { picking = true }
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

/**
 * A row holding one **day of the week**, picked from all seven.
 *
 * A dialog of radio buttons rather than the app's [SegmentedChoice]: that control is for a choice between
 * a fixed, mutually exclusive *few* — two to four, past which a segment is narrower per label than a phone
 * can render — and seven Russian weekday names are far past it. Radios in a dialog are what the app
 * already uses for a one-of-many choice that has no room in a row (the student card's subject picker).
 *
 * The stored integer is read through [Digests.dayOfWeekOf], the same total function the scheduler uses, so
 * a value outside 1..7 shows as Monday here and fires on Monday there rather than the two disagreeing.
 */
@Composable
internal fun WeekdayRow(
    title: String,
    dayOfWeek: Int,
    enabled: Boolean,
    onSet: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val names = stringArrayResource(R.array.weekday_names)
    val selected = Digests.dayOfWeekOf(dayOfWeek)

    ValueRow(title = title, value = names[selected.isoDayNumber - 1], enabled = enabled) { picking = true }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(title) },
            text = {
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
            },
            // No «Сохранить»: picking a day *is* the answer, and a confirm button would ask the tutor to
            // agree with the tap they have just made. «Готово» closes a picker, «Сохранить» writes — this
            // writes on the tap, so it only needs the way out.
            confirmButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The shape both rows above share: a title, the current value in the accent colour on the right, and the
 * whole row opening whatever picks it. The same shape [MoneyRow] has, dimmed when the setting it depends
 * on is off.
 */
@Composable
private fun ValueRow(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = DisabledAlpha)
                },
            )
        },
        colors = if (enabled) {
            ListItemDefaults.colors()
        } else {
            ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
            )
        },
        modifier = Modifier.insetRowPress {
            Modifier.clickable(it, indication = null, enabled = enabled, onClick = onClick)
        },
    )
}

/**
 * A titled row holding one money amount, edited in a dialog. Shows the amount itself as the value, so
 * the row reads as a statement of the setting rather than as a link to go and look.
 *
 * **Clearing the field unsets it** (null, «Не задана»), which is why the dialog's save stays enabled on
 * an empty field: one control both sets and removes the default, instead of an amount the tutor can
 * never take back once typed.
 */
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
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.insetRowPress { Modifier.clickable(it, indication = null) { editing = true } },
    )
    if (editing) {
        // Opens on the amount currently set, so editing it is a correction rather than a re-entry.
        // OPTIONAL_RATE is what makes clearing the box mean "not set" rather than being invalid. The text is
        // saveable and the field derived from it, as every typed dialog field is.
        var text by rememberSaveable { mutableStateOf(amount?.toAmountInput().orEmpty()) }
        val field = MoneyField(text, MoneyField.Kind.OPTIONAL_RATE)
        AlertDialog(
            onDismissRequest = { editing = false },
            // The rule every dialog the tutor types into follows: a stray tap beside the box must not
            // throw away the amount just entered. See `KeepTypedInput`, where the reasoning is —
            // «Отмена» is still there for leaving on purpose, and back still closes it.
            properties = KeepTypedInput,
            title = { Text(title) },
            text = {
                MoneyTextField(
                    field = field,
                    onValueChange = { text = it },
                    label = stringResource(R.string.price_label),
                    // Like every other one-field dialog: the caret already in it, since a price is the only
                    // thing it is opened to do.
                    modifier = Modifier.focusRequester(rememberRevealedFocus()),
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

/**
 * The system page where notifications are switched back on for this app.
 *
 * `ACTION_APP_NOTIFICATION_SETTINGS` lands straight on this app's own notification page and exists from
 * Android 8. Below that there is no such page, and the app's details screen is the closest thing — one
 * tap from the same switch. `minSdk` here is 24, so both arms are genuinely reachable.
 */
internal fun appNotificationSettings(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        appDetailsSettings(context)
    }

/** This app's own page in system settings — where a permission refused for good is given back. */
internal fun appDetailsSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/**
 * The system page where an exact alarm is granted.
 *
 * This is the app **offering** the page, not demanding the permission: it still declares only
 * `SCHEDULE_EXACT_ALARM` and not `USE_EXACT_ALARM` (which Play reserves for alarm-clock and calendar
 * apps and which cannot be revoked), and nothing reaches here except a row the tutor tapped. The
 * distinction the manifest draws is between prompting and being askable, and this is the latter.
 *
 * `@SuppressLint("InlinedApi")`: the action is a compile-time `String` constant, so naming it costs
 * nothing on an older version — and the only row that opens it cannot be drawn below Android 12.
 */
@SuppressLint("InlinedApi")
internal fun exactAlarmSettings(context: Context): Intent =
    Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null),
    )

/** A small, tinted section label that breaks the settings list into scannable groups. */
@Composable
internal fun SectionHeader(title: String) {
    // The app's section title, tinted as a preference category is — said as a heading, so the long list
    // can be walked by section.
    SectionTitle(
        title,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.sectionGap, bottom = Spacing.tight),
    )
}
