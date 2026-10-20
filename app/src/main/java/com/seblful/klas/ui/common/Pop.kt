package com.seblful.klas.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
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

/** eld enlarged while [armed] — a drag past its mark, which letting go would act on. */
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
 */
@Composable
fun Modifier.popsOnChange(value: Any?): Modifier {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value != previous) {
            previous = value
            scale.snapTo(ChangeScale)
            scale.animateTo(1f, PopSpring)
        }
    }

    return this.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

private val PopSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private const val ArmedScale = 1.3f

private const val ChangeScale = 1.18f
