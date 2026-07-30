package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse

/**
 * **One control for a choice between a fixed, mutually exclusive few**, wherever the app asks for one.
 *
 * The app had settled on `SingleChoiceSegmentedButtonRow` in seven places — the schedule's view mode,
 * the lesson status in the action sheet, People's two sides, and four settings rows — and then the
 * booking form asked "student or group?" with a pair of `FilterChip`s. A two-way exclusive switch at the
 * very top of the app's most-used form should look like the other two-way exclusive switches; chips are
 * for a *shortlist with an escape hatch*, which is exactly what the duration chips below it are and
 * exactly what this was not.
 *
 * @param options the values, in the order they should appear. Two to four; past that a row of segments
 *   is narrower per label than a phone can render.
 * @param label how a value reads to the tutor. Drawn through [SegmentLabel], so every segmented control
 *   in the app shrinks its text the same way instead of only the one that happened to need it first.
 * @param enabled whether a particular option may be chosen. The default lets everything be picked; the
 *   lesson-status row uses it, since which statuses a lesson may move to is `GroupRules`'.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    label: @Composable (T) -> String,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(option) },
                enabled = isSelected || enabled(option),
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                // No check mark. Its slot costs ~28dp of a segment that may be one third of a phone,
                // and the selected segment already carries a tinted container — which is Material's own
                // affordance, and is what the tick was duplicating.
                icon = {},
            ) { SegmentLabel(label(option)) }
        }
    }
}

/**
 * A segment's label on **one line**, shrinking a step at a time rather than wrapping.
 *
 * The same rule and mechanism as the bottom bar's tab labels: a segment is a fixed fraction of the
 * screen, so on a narrow phone or at a large font scale the longest word genuinely does not fit — and a
 * wrapped label makes the row uneven, which is worse than a slightly smaller one. Capped at the
 * inherited size so a label with room to spare is drawn untouched.
 *
 * Public, and in `ui/common/`, because it was private to `ScheduleScreen.kt` — so the schedule's own
 * segments shrank and the settings rows, People's two sides and the booking form's target did not.
 */
@Composable
fun SegmentLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        // Only reachable if even the minimum overflows; wrapping is what we are avoiding, so cut the
        // word rather than add a line.
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MinSegmentFontSize,
            maxFontSize = LocalTextStyle.current.fontSize.takeOrElse { FallbackSegmentFontSize },
        ),
    )
}

private val MinSegmentFontSize = 10.sp

/** Material's label size for a segmented button; used only if the style leaves the size unspecified. */
private val FallbackSegmentFontSize = 14.sp
