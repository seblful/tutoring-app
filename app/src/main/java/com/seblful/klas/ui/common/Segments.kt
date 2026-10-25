package com.seblful.klas.ui.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.seblful.klas.ui.common.Spacing
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
 * @param colors for a row on a tinted card, where the default selected fill is the card's own tint.
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
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
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
                colors = colors,
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

/** Whether a choice of [optionCount] values is asked with segments, or with a field instead. */
fun fitsInSegments(optionCount: Int): Boolean = optionCount <= MaxSegments

/** A labelled row for **picking one of a fixed few** — the shape a *setting* takes. */
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
    // Remembered: a new list each pass would keep the control below from ever skipping.
    val values = remember(options) { options.map { it.first } }
    if (!fitsInSegments(options.size) || LocalDensity.current.fontScale >= 1.5f) {
        // A field, whose own label and supporting slot carry the two lines the segmented version draws
        // above itself — «everything that qualifies a field goes in that field's `supportingText`».
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.inline)) {
            ChoiceField(
                label = title,
                selected = selected,
                options = values,
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
            options = values,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.padding(top = Spacing.inline),
            onReselect = onReselect,
            label = { value -> options.first { it.first == value }.second },
        )
    }
}

/** A segment's label on **one line**, shrinking a step at a time rather than wrapping. */
@Composable
fun SegmentLabel(text: String) {
    OneLineLabel(text, floor = MinSegmentLabelSize, fallbackSize = MaterialTheme.typography.labelLarge.fontSize)
}

/**
 * A label held to **one line**, shrinking a step at a time — never below [floor] — before it ellipsizes:
 * the rule a segment's label and a bottom-bar tab's share. They differ only in how small they may go and
 * in the size their style is assumed to ask for when it says nothing.
 *
 * @param fallbackSize the ceiling when the inherited style leaves the size unspecified.
 */
@Composable
fun OneLineLabel(text: String, floor: TextUnit, fallbackSize: TextUnit) {
    Text(
        text = text,
        maxLines = 1,
        // Only reachable if even the minimum overflows; wrapping is what we are avoiding, so cut the
        // word rather than add a line.
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = floor,
            maxFontSize = LocalTextStyle.current.fontSize.takeOrElse { fallbackSize },
        ),
    )
}

private val MinSegmentLabelSize = 10.sp

/**
 * A screen whose body is **one of a fixed few views**, picked by tabs pinned under the bar — «Люди»'s
 * two sides, «Задания»'s current and archive, the schedule's day, week and month.
 *
 * Tabs, not [SegmentedChoice]: moving between views is navigation, and segments are kept for *values* in
 * forms and sheets, where their outline matches the outlined fields around them.
 *
 * @param header what else is pinned under the tabs — a filter chip, the schedule's pager.
 * @param content the body, under the header; it takes the drag's free space, not the header's alone.
 */
@Composable
fun <T> SegmentedPage(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    headerAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    headerMaxWidth: Dp = MaxContentWidth,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    OpensFirstViewOnReselect(onFirst = selected == options.first()) { onSelect(options.first()) }
    Column(modifier.swipeBetweenSegments(options, selected, onSelect)) {
        ScreenHeader {
            // Held to a column's width even over a body that grows wider, so the tabs do not spread apart —
            // or, over two panes, to the pane the header's pager turns ([headerAlignment], [headerMaxWidth]).
            Column(Modifier.fillMaxWidth().wrapContentWidth(headerAlignment).widthIn(max = headerMaxWidth)) {
                ViewTabs(options, selected, onSelect, label)
                header()
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ViewTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
) {
    val tick = rememberSegmentTickFeedback()
    PrimaryTabRow(
        selectedTabIndex = options.indexOf(selected).coerceAtLeast(0),
        // The header paints the bar's colour behind it, at rest and tinted on scroll alike.
        containerColor = Color.Transparent,
        // No rule under the tabs: the header carries on below them (the schedule's pager) as one band.
        divider = {},
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val press = remember { MutableInteractionSource() }
            // Material's tab ripple fills its whole cell, a hard-edged block from the bar's top to the indicator;
            // the press is drawn rounded around the label instead, as rows press within an inset shape.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Tab(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            tick()
                            onSelect(option)
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    interactionSource = press,
                    text = {
                        CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration()) {
                            Box(
                                // Reaching into the tab's own padding, so the label keeps its width.
                                Modifier
                                    .bleed(TabPressPadX)
                                    .clip(MaterialTheme.shapes.small)
                                    .indication(press, ripple())
                                    .padding(horizontal = TabPressPadX, vertical = TabPressPadY),
                            ) { SegmentLabel(label(option)) }
                        }
                    },
                )
            }
        }
    }
}

private val TabPressPadX = 12.dp
private val TabPressPadY = 6.dp

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
    val direction by rememberUpdatedState(LocalLayoutDirection.current)

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
                segmentAfterSwipe(choices, current, forward = swipeIsForward(travelled, direction))?.let { next ->
                    tick()
                    select(next)
                }
            },
        )
    }
}

/**
 * Dragging toward the **start** moves forward: the page pulled off that way is the next one arriving,
 * which is the direction the app is read in — leftward, or rightward in a right-to-left language.
 */
internal fun swipeIsForward(travelled: Float, direction: LayoutDirection): Boolean =
    if (direction == LayoutDirection.Ltr) travelled < 0f else travelled > 0f

internal fun <T> segmentAfterSwipe(options: List<T>, selected: T, forward: Boolean): T? {
    val at = options.indexOf(selected)
    if (at < 0) return null
    return options.getOrNull(if (forward) at + 1 else at - 1)
}

private val SegmentSwipeDistance = 56.dp
