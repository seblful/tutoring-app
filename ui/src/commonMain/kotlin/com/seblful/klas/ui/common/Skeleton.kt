package com.seblful.klas.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.delay

/** What a screen draws while its state is [Loadable.Loading]. */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    rowHeight: Dp = SkeletonRowHeight,
    contentPadding: PaddingValues = PaddingValues(Spacing.gutter),
    pulse: SkeletonPulse = rememberSkeletonPulse(),
) {
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { testTag = SKELETON_TAG }
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        repeat(rows) { SkeletonBlock(rowHeight, pulse = pulse) }
    }
}

/**
 * One placeholder shape, at the tonal ladder's item rung so it reads as a card that has not filled in
 * yet rather than as a hole in the page.
 *
 * @param height the height of the row being stood in for. The caller knows it; this does not.
 */
@Composable
fun SkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    pulse: SkeletonPulse = rememberSkeletonPulse(),
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = pulse.alpha
    val shown = pulse.shown
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag(SKELETON_TAG)
            .clip(shape)
            // The pulse is read in the draw, not in composition: read here it recomposed every block of
            // every skeleton on every frame of the wait, to repaint one colour.
            .drawBehind { if (shown.value) drawRect(tint.copy(alpha = alpha.value)) },
    ) {}
}

/**
 * What every block of one skeleton draws by: one pulse and one delay, shared — each block keeping its own ran
 * a transition and a timer per block, all saying the same.
 */
class SkeletonPulse internal constructor(internal val alpha: State<Float>, internal val shown: State<Boolean>)

/** A [SkeletonPulse] for the blocks of one skeleton to share. */
@Composable
fun rememberSkeletonPulse(): SkeletonPulse {
    val alpha = placeholderAlpha()
    val shown = rememberPlaceholderShown()
    return remember(alpha, shown) { SkeletonPulse(alpha, shown) }
}

/**
 * Whether a placeholder may be drawn yet: **not for the first [PlaceholderDelayMillis] of a wait.**
 *
 * @param waiting whether there is anything to wait for; the delay starts again each time it turns true.
 */
@Composable
fun rememberPlaceholderShown(waiting: Boolean = true): State<Boolean> {
    val shown = remember(waiting) { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        if (waiting) {
            delay(PlaceholderDelayMillis)
            shown.value = true
        }
    }
    return shown
}

@Composable
private fun placeholderAlpha(): State<Float> {
    val animate = rememberAnimationsEnabled()
    if (!animate) return remember { mutableFloatStateOf((MinPlaceholderAlpha + MaxPlaceholderAlpha) / 2f) }

    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = MinPlaceholderAlpha,
        targetValue = MaxPlaceholderAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PulseMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
}

/**
 * A student's or group's card before it has been read: the line under the name, the two headline cards, and
 * a section.
 *
 * @param onBack the back arrow stays live throughout. A screen that cannot be left until it has loaded
 * is a screen that traps the tutor if the load is slow. Null beside a list, as on the loaded card.
 */
@Composable
fun LoadingDetailScreen(onBack: (() -> Unit)?) {
    KlasScreen(
        // No title: the title is the name of the thing being loaded, and inventing a placeholder for it
        // would be the one piece of this screen that lies rather than waits.
        title = null,
        onBack = onBack,
    ) { modifier, bottomInset ->
        Column(
            modifier.padding(bottomInset).padding(horizontal = Spacing.gutter, vertical = Spacing.itemGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            val pulse = rememberSkeletonPulse()
            SkeletonBlock(
                FactsLineHeight,
                Modifier.fillMaxWidth(FactsLineWidth),
                shape = MaterialTheme.shapes.extraSmall,
                pulse = pulse,
            )
            repeat(2) { SkeletonBlock(HeadlineCardHeight, pulse = pulse) }
            SkeletonBlock(SectionHeight, pulse = pulse)
        }
    }
}

/**
 * A form before the record it edits has been read: the fields, in order, at the size they will be.
 *
 * @param fields how many rows the form opens with. The caller knows; this does not.
 */
@Composable
fun LoadingFormScreen(title: String, onBack: () -> Unit, fields: Int = 4) {
    KlasScreen(title = title, onBack = onBack) { modifier, bottomInset ->
        Column(
            modifier.padding(bottomInset).padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
        ) {
            val pulse = rememberSkeletonPulse()
            repeat(fields) { SkeletonBlock(FieldHeight, pulse = pulse) }
        }
    }
}

/** On every placeholder, so a screenshot can wait until the screen has been read. */
const val SKELETON_TAG = "skeleton"

private val SkeletonRowHeight = 72.dp

private val FieldHeight = 72.dp

private val FactsLineHeight = 20.dp

private const val FactsLineWidth = 0.6f

private val HeadlineCardHeight = 96.dp

private val SectionHeight = 240.dp

private const val PlaceholderDelayMillis = 150L

private const val MinPlaceholderAlpha = 0.06f
private const val MaxPlaceholderAlpha = 0.14f
private const val PulseMillis = 900
