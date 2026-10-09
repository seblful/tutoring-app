package com.seblful.klas.ui.common

import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.delay

/**
 * What a screen draws while its state is [Loadable.Loading].
 *
 * **Shaped like the content, not a spinner in the middle.** A centred indicator gives the eye nothing to
 * settle on and then reflows the whole screen when the data lands; blocks the size of the rows that are
 * coming mean the first real frame changes colour rather than layout. That is the difference between a
 * list that loads and a list that jumps.
 *
 * Silent to a screen reader. A skeleton is scenery for a wait, and announcing five unlabelled blocks
 * would be worse than announcing nothing — the content that replaces them is what has something to say.
 */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    rowHeight: Dp = SkeletonRowHeight,
    contentPadding: PaddingValues = PaddingValues(Spacing.gutter),
) {
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        repeat(rows) { SkeletonBlock(rowHeight) }
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
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = placeholderAlpha()
    val shown = rememberPlaceholderShown()
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            // The pulse is read in the draw, not in composition: read here it recomposed every block of
            // every skeleton on every frame of the wait, to repaint one colour.
            .drawBehind { if (shown.value) drawRect(tint.copy(alpha = alpha.value)) },
    ) {}
}

/**
 * Whether a placeholder may be drawn yet: **not for the first [PlaceholderDelayMillis] of a wait.**
 *
 * Most waits here are a local query and end within a few frames, and a skeleton drawn for those frames is
 * not a loading state anybody reads — it is grey blocks blinking in and out between two screens, which is
 * the glitch it was meant to prevent. Held back, a fast load goes straight from the page to its content;
 * a slow one still gets its skeleton, a moment later. The space is reserved from the first frame either
 * way, so nothing reflows when it appears.
 *
 * Read the result in the draw phase, as [SkeletonBlock] does, so the moment it flips repaints rather than
 * recomposes.
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

/**
 * The faint pulse that says the wait is live.
 *
 * **Held still where the phone asks for it.** Animations can be switched off system-wide (developer
 * options, or a battery saver), and an app that keeps pulsing through that setting is ignoring the same
 * kind of instruction the app already honours for haptics — see `Haptics.kt`. Read once per composition
 * rather than observed: this is a setting a tutor changes in system settings, which restarts the
 * activity anyway.
 */
@Composable
private fun placeholderAlpha(): State<Float> {
    val context = LocalContext.current
    val animate = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
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
 * A detail card before its subject has been read: the stat strip, the tab row, and rows under it.
 *
 * Shared by the student's card and the group's, which have the same skeleton because they have the same
 * skeleton — numbers on top, a tab row, one list. Their *contents* differ and their loading shape does
 * not, so this is one composable rather than the two near-identical private ones the cards would
 * otherwise have grown (they already did that with `TabEmptyState`).
 *
 * @param onBack the back arrow stays live throughout. A screen that cannot be left until it has loaded
 *   is a screen that traps the tutor if the load is slow.
 */
@Composable
fun LoadingDetailScreen(onBack: () -> Unit) {
    KlasScreen(
        // No title: the title is the name of the thing being loaded, and inventing a placeholder for it
        // would be the one piece of this screen that lies rather than waits.
        title = null,
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.itemGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            SkeletonBlock(StatRowHeight)
            SkeletonBlock(TabRowHeight, shape = MaterialTheme.shapes.extraSmall)
            ListSkeleton(rows = 4, rowHeight = DetailRowHeight, contentPadding = PaddingValues(0.dp))
        }
    }
}

/**
 * A form before the record it edits has been read: the fields, in order, at the size they will be.
 *
 * **The title is known and is passed in, unlike [LoadingDetailScreen]'s.** A form knows whether it is
 * adding or editing before it knows anything else — that comes from the navigation argument, not from the
 * database — so «Изменить занятие» is a fact here and «Иван Петров» is not.
 *
 * A form is the one place where drawing the loaded-yet state as *content* would be worst: an
 * `OutlinedTextField` bound to a value that has not arrived is not a blank field, it is a field
 * **stating** that the lesson has no student and the group no name, over the tutor's real data, with a
 * required-field hint under it and «Нет групп» in the error red beside it. Waiting is the only honest
 * thing a form can draw.
 *
 * No save action: there is nothing yet to save, and an enabled one would act on the empty form. The back
 * arrow stays live for [LoadingDetailScreen]'s reason, and needs no unsaved-changes question — a form
 * that has not loaded cannot hold work.
 *
 * @param fields how many rows the form opens with. The caller knows; this does not.
 */
@Composable
fun LoadingFormScreen(title: String, onBack: () -> Unit, fields: Int = 4) {
    KlasScreen(title = title, onBack = onBack) { modifier ->
        Column(
            modifier.padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
        ) {
            repeat(fields) { SkeletonBlock(FieldHeight) }
        }
    }
}

/** Roughly a `ContentCard` holding a 40dp avatar and two lines of text. */
private val SkeletonRowHeight = 72.dp

/** Roughly an `OutlinedTextField` with a label and a line of supporting text under it. */
private val FieldHeight = 72.dp

/** Roughly a `StatRow`: three tiles of a value, a label and a hint, at 12dp vertical padding. */
private val StatRowHeight = 76.dp

/** Roughly a `SecondaryTabRow`. */
private val TabRowHeight = 48.dp

/** Roughly a `ListItem` with a leading avatar and a supporting line. */
private val DetailRowHeight = 64.dp

/** Longer than a local query takes on a mid-range phone, shorter than a wait anybody notices. */
private const val PlaceholderDelayMillis = 150L

private const val MinPlaceholderAlpha = 0.06f
private const val MaxPlaceholderAlpha = 0.14f
private const val PulseMillis = 900
