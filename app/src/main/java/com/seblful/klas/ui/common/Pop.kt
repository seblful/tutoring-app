package com.seblful.klas.ui.common

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import com.seblful.klas.ui.theme.KlasMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * **A moment the tutor feels should also be a moment they see.** The two pops here are the visible halves
 * of the two things a swipe-to-complete says (`ui/common/SwipeToConfirm`, `ui/common/Haptics`): *the promise
 * stands* while a drag is past its mark, and *it happened* when the status flips.
 *
 * They are one file because they are one idea in two shapes — a spring scale, from one spec — and because
 * haptics can be switched off on the phone while animation cannot. A gesture whose only answer was a buzz
 * would say nothing at all to a tutor who has vibration off; this is the half that always arrives.
 *
 * Both draw through [graphicsLayer], so the pop costs a redraw and never a re-layout: the label beside a
 * popping icon does not shift, and a row in a `LazyColumn` does not change height while the list is being
 * dragged.
 */

/** Held enlarged while [armed] — a drag past its mark, which letting go would act on. */
@Composable
fun Modifier.armedPop(armed: Boolean): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (armed) ArmedScale else 1f,
        animationSpec = PopSpring,
        label = "armedPop",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * A pop each time [value] changes — for a fact that changes *under* the tutor: a status badge flipping to
 * «Проведено» because a row was just swiped, or corrected from the sheet.
 *
 * A node rather than a composable modifier: it is on every schedule card, and at rest it costs a list item
 * nothing to compose — no remembered state, no effect.
 */
fun Modifier.popsOnChange(value: Any?): Modifier = this then PopOnChangeElement(value)

private data class PopOnChangeElement(val value: Any?) : ModifierNodeElement<PopOnChangeNode>() {
    override fun create() = PopOnChangeNode(value)

    override fun update(node: PopOnChangeNode) = node.changeTo(value)

    override fun InspectorInfo.inspectableProperties() {
        name = "popsOnChange"
        properties["value"] = value
    }
}

private class PopOnChangeNode(private var value: Any?) : Modifier.Node(), LayoutModifierNode {
    private var scale by mutableFloatStateOf(1f)
    private var pop: Job? = null

    // A lazy list hands a scrolled-off card's node to the next card: that card's value is not a change.
    private var reused = false

    fun changeTo(next: Any?) {
        val changed = next != value && !reused
        value = next
        reused = false
        if (changed && isAttached) {
            pop?.cancel()
            pop = coroutineScope.launch {
                animate(ChangeScale, 1f, animationSpec = PopSpring) { current, _ -> scale = current }
            }
        }
    }

    override fun onReset() {
        reused = true
        scale = 1f
    }

    // The pop is cancelled with the node's scope; whatever it stopped at must not greet the card's return.
    override fun onDetach() {
        scale = 1f
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

private val PopSpring = KlasMotion.bouncySpatial<Float>()

private const val ArmedScale = 1.3f

private const val ChangeScale = 1.18f
