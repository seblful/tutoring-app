package com.seblful.klas.ui.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.seblful.klas.ui.common.state.map
import kotlin.math.abs

/**
 * **One control for a choice between a fixed, mutually exclusive few**, wherever the app asks for one.
 *
 * @param options the values, in the order they should appear. Two to [MaxSegments]; past that a row of
 * segments is narrower per label than a phone can render, and a growing list of options wants
 * [ChoiceRow], which changes control rather than shrinking past legibility.
 * @param label how a value reads to the tutor. Drawn through [SegmentLabel], so every segmented control
 * in the app shrinks its text the same way instead of only the one that happened to need it first.
 * @param enabled whether a particular option may be chosen. The default lets everything be picked; the
 * lesson-status row uses it, since which statuses a lesson may move to is `GroupRules`'.
 * @param onReselect a tap on the segment already selected, for the one kind of segment that is a door
 * rather than a value — Settings' «Другое», which opens a picker. Without it a custom reminder lead
 * could not be changed to another custom one: the tap on the segment showing «20 мин» did nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    onReselect: ((T) -> Unit)? = null,
    label: @Composable (T) -> String,
) {
    // Here rather than at the eight call sites, which is the point of this composable existing: the tick is
    // a property of "picking a segment" and not of any one row that happens to be made of segments. It fires
    // only where the selection **moves** — the `if (!isSelected)` below is the one place that decides that,
    // so a tutor re-tapping the segment they are already on gets the silence they should.
    val segmentTick = rememberSegmentTickFeedback()

    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            SegmentedButton(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        segmentTick()
                        onSelect(option)
                    } else {
                        onReselect?.invoke(option)
                    }
                },
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
 * The most options a row of segments can carry — [SegmentedChoice]'s own stated bound, named here because
 * [ChoiceRow] is what enforces it.
 */
const val MaxSegments = 4

/** hether a choice of [optionCount] values is asked with segments, or with a field instead. */
fun fitsInSegments(optionCount: Int): Boolean = optionCount <= MaxSegments

/**  labelled row for **picking one of a fixed few** — the shape a *setting* takes. */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    description: String? = null,
    /** See [SegmentedChoice]'s `onReselect`. A field already offers every option, so only segments need it. */
    onReselect: ((T) -> Unit)? = null,
) {
    if (!fitsInSegments(options.size)) {
        // A field, whose own label and supporting slot carry the two lines the segmented version draws
        // above itself — «everything that qualifies a field goes in that field's `supportingText`».
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.inline)) {
            ChoiceField(
                label = title,
                selected = selected,
                options = options.map { it.first },
                optionLabel = { value -> options.first { it.first == value }.second },
                onSelect = onSelect,
                supportingText = description,
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.inline)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        // The qualifier goes **under the label and above the control**, the place a `ListItem`'s supporting
        // text would be: it explains the question, not the answer that happens to be selected. Only the
        // setup's currency row uses it — every settings row's title is its own explanation.
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.tight),
            )
        }
        // Through [SegmentedChoice], so every one of these gets the one-line shrink rule the schedule's
        // segments already had; «Как в системе» in a third of a phone is exactly the label that needed it.
        SegmentedChoice(
            options = options.map { it.first },
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.padding(top = Spacing.inline),
            onReselect = onReselect,
            label = { value -> options.first { it.first == value }.second },
        )
    }
}

/**  segment's label on **one line**, shrinking a step at a time rather than wrapping. */
@Composable
fun SegmentLabel(text: String) {
    OneLineLabel(text, floor = MinSegmentLabelSize, fallbackSize = FallbackSegmentFontSize)
}

/**
 * A label held to **one line**, shrinking a step at a time — never below [floor] — before it ellipsizes:
 * the rule a segment's label and a bottom-bar tab's share. They differ only in how small they may go and
 * in the size their style is assumed to ask for when it says nothing.
 *
 * @param fallbackSize the ceiling when the inherited style leaves the size unspecified.
 */
@Composable
fun OneLineLabel(text: String, floor: Dp, fallbackSize: TextUnit) {
    Text(
        text = text,
        maxLines = 1,
        // Only reachable if even the minimum overflows; wrapping is what we are avoiding, so cut the
        // word rather than add a line.
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = with(LocalDensity.current) { floor.toSp() },
            maxFontSize = LocalTextStyle.current.fontSize.takeOrElse { fallbackSize },
        ),
    )
}

private val MinSegmentLabelSize = 10.dp

private val FallbackSegmentFontSize = 14.sp

/**
 * A screen whose body is **one of a fixed few views**, picked by segments pinned under the bar — «Люди»'s
 * two sides, «Задания»'s current and archive, the schedule's day, week and month.
 *
 * @param header what else is pinned under the segments — a filter chip, the schedule's pager.
 * @param content the body, under the header; it takes the drag's free space, not the header's alone.
 */
@Composable
fun <T> SegmentedPage(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.swipeBetweenSegments(options, selected, onSelect)) {
        ScreenHeader {
            SegmentedChoice(
                options = options,
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
                label = label,
            )
            header()
        }
        content()
    }
}

/**
 * **The other way to move between segments: drag the page sideways.**
 *
 * @param options the segments, in the order they are drawn — the same list handed to [SegmentedChoice].
 * @param selected which one the screen is on now.
 * @param onSelect told the neighbour the drag landed on. Never called with [selected].
 */
@Composable
fun <T> Modifier.swipeBetweenSegments(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
): Modifier {
    val tick = rememberSegmentTickFeedback()
    // Read through `rememberUpdatedState` so the detector below can be keyed on `Unit`. This is not a
    // saving — it is the correctness of "one gesture, one step". Keying the `pointerInput` on `selected`
    // would tear the detector down and rebuild it at the instant the selection moves, which is to say
    // *mid-drag*: the new one starts from the pointer where it is, having never seen the distance already
    // travelled, and one long swipe would walk День → Неделя → Месяц a threshold at a time.
    val current by rememberUpdatedState(selected)
    val choices by rememberUpdatedState(options)
    val select by rememberUpdatedState(onSelect)

    return this.pointerInput(Unit) {
        val threshold = SegmentSwipeDistance.toPx()
        // Net displacement, so a wobble that ends where it began is not a swipe, and a drag taken back
        // half way costs nothing.
        var travelled = 0f
        // Whether this gesture has had its answer. Set even where the answer was "nowhere to go", so a
        // tutor already on the last segment cannot drag on and reverse into the previous one.
        var decided = false
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f; decided = false },
            onHorizontalDrag = { _, delta ->
                if (decided) return@detectHorizontalDragGestures
                travelled += delta
                if (abs(travelled) < threshold) return@detectHorizontalDragGestures
                decided = true
                // Dragging **left** is negative and moves forward: the page being pulled off to the left
                // is the next one arriving, which is the direction the app is read in.
                segmentAfterSwipe(choices, current, forward = travelled < 0f)?.let { next ->
                    tick()
                    select(next)
                }
            },
        )
    }
}

internal fun <T> segmentAfterSwipe(options: List<T>, selected: T, forward: Boolean): T? {
    val at = options.indexOf(selected)
    if (at < 0) return null
    return options.getOrNull(if (forward) at + 1 else at - 1)
}

private val SegmentSwipeDistance = 56.dp
