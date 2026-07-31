package com.seblful.klas.ui.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import kotlin.math.abs

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
 * @param options the values, in the order they should appear. Two to [MaxSegments]; past that a row of
 *   segments is narrower per label than a phone can render, and a growing list of options wants
 *   [ChoiceRow], which changes control rather than shrinking past legibility.
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

/**
 * Whether a choice of [optionCount] values is asked with segments, or with a field instead.
 *
 * A named answer rather than an `if` inside the composable, for the reason every other one in this app is:
 * a `@Composable` can only be exercised on a device, and this is the rule that decides what a *growing*
 * list of options looks like — the language and the currency both gain an entry whenever the app learns one.
 * `SegmentsTest` pins the boundary on the JVM; `ChoiceRowTest` pins that each side really draws the control
 * it names.
 */
fun fitsInSegments(optionCount: Int): Boolean = optionCount <= MaxSegments

/**
 * A labelled row for **picking one of a fixed few** — the shape a *setting* takes.
 *
 * Here rather than private to `SettingsScreen`, which is where it was written: first-run setup asks two of
 * the same questions Settings does — the language and the currency — and a choice that looks like a setting
 * on one screen and like something else on the other would be the app disagreeing with itself about what
 * kind of question it is asking. The same argument moved `AddRow` into this package.
 *
 * **Which control it draws is decided by how many options there are**, and that is the point of the row
 * existing rather than each screen reaching for [SegmentedChoice] itself. Up to [MaxSegments] it is
 * segments; past that it is a [ChoiceField], because a row of segments is a fixed fraction of the screen
 * divided by the number of them and at five there is no width left to shrink a label into. This app has two
 * settings that are *lists which grow* — the language and the currency, one entry per translation and per
 * currency the app learns — so without this the fifth of either would have silently produced an unreadable
 * row rather than a compile error or a visibly different control. The other five call sites are choices with
 * a fixed arity (a theme, a lesson length, two sides of a tab) and never reach the bound.
 *
 * Both controls are already the app's own answers to their respective questions, so this switches between
 * two established shapes rather than inventing a third: see *one control per kind of question*.
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    description: String? = null,
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
            label = { value -> options.first { it.first == value }.second },
        )
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

/**
 * **The other way to move between segments: drag the page sideways.**
 *
 * Applied to a screen's body, so the tutor changes what they are looking at from wherever their thumb
 * already is instead of reaching back up to the control. Drag left for the next option, right for the
 * previous — the direction the app is read in. It is offered on the two screens whose whole content is one
 * of a fixed few views of the same thing: «Люди» (Ученики ⇄ Группы) and «Расписание» (День → Неделя →
 * Месяц).
 *
 * **The segments stay, and stay primary.** This adds a shortcut, never the only way in: a gesture has no
 * affordance, announces nothing to a screen reader and cannot be discovered by looking, so the control it
 * duplicates is what makes the screen usable and this is what makes it quick. That is also why the tick is
 * [rememberSegmentTickFeedback] — the same effect a tap on the control gives, because it is the same move,
 * and the app's rule is that one act feels the same whichever way it was made.
 *
 * **A child that wants the drag keeps it, and that is the design rather than a limitation.** The schedule's
 * lesson cards own their own horizontal gesture ([SwipeToConfirmBox], which *bills* a lesson), and a child
 * consumes past touch slop before an ancestor is ever offered the event. So the page turns from the free
 * space around the cards — the gutters, the gaps, the headers, the calendar grid, an empty day — and a drag
 * that starts on a lesson still marks that lesson taught. Neither gesture had to be given up for the other,
 * and neither needs to know the other exists. A vertical scroll is unaffected for the mirror-image reason:
 * a `LazyColumn` reaches *its* slop first and consumes, so a flick down the list never changes the view.
 *
 * **The content does not follow the finger, and cannot.** The obvious shape for this is a `HorizontalPager`,
 * with the incoming view sliding in under the thumb. The schedule assembles only the active view's lessons
 * and markers — both flows are `flatMapLatest`-keyed on the view mode, deliberately, so no off-screen view
 * holds a live query — so the neighbouring page has nothing to draw until the switch has already happened.
 * A pager would drag a skeleton into view and fill it in afterwards, which is a worse answer than not
 * moving at all. The same gesture is used on both screens rather than the nicer one on the one screen that
 * could support it: a drag that tracks the finger here and jumps there is two gestures wearing one name.
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

/**
 * Where a swipe lands: the neighbour of [selected] in [options], or **null at either end**.
 *
 * **It does not wrap**, which is the whole of the rule and the reason it is a function. A segmented control
 * is a row of detents and not a carousel: the pill sits somewhere along a line the tutor can see, so
 * dragging on past «Месяц» and arriving at «День» would be the selection jumping the full width of the
 * control in the direction opposite to the thumb. Running out is the honest answer, and it is the same
 * answer tapping the segment you are already on gives — nothing, in silence.
 *
 * Pure and outside the [Modifier], for the reason [swipeArmed] is: a rule that lives inside a gesture
 * callback is a rule only a device can ask about. Null for a [selected] that is not in [options] rather
 * than an assumed first — a swipe is not the place to discover that a caller's two lists disagree.
 */
internal fun <T> segmentAfterSwipe(options: List<T>, selected: T, forward: Boolean): T? {
    val at = options.indexOf(selected)
    if (at < 0) return null
    return options.getOrNull(if (forward) at + 1 else at - 1)
}

/**
 * How far the page has to travel before it turns.
 *
 * A **fixed distance**, not a fraction of the screen the way [swipeArmed]'s mark is a fraction of its row:
 * that mark is about a row the thumb has to cross, whereas this is one act — "next view" — and it should
 * not cost twice the drag on a tablet that it costs on a phone. 56dp sits past the touch slop that starts
 * the drag, so a sideways wobble while scrolling the schedule cannot turn the page, and well inside a
 * thumb's reach on a 360dp phone, since this is a shortcut and a shortcut that needs the whole hand is not
 * one.
 */
private val SegmentSwipeDistance = 56.dp
