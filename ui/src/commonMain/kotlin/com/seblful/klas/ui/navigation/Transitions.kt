package com.seblful.klas.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import com.seblful.klas.ui.theme.KlasMotion

/**
 * **Every destination arrives the same way: Material's fade through.** One rule, no direction: the screen
 * leaving fades out first, then the arriving one fades in as it grows from 92%, so the two never show at
 * half strength together. Onboarding's steps and the way into the app arrive by it too.
 */
fun <S> AnimatedContentTransitionScope<S>.fadeThrough(): ContentTransform =
    (
        fadeIn(tween(InMillis, delayMillis = OutMillis, easing = KlasMotion.EmphasizedDecelerate)) +
            scaleIn(tween(InMillis, delayMillis = OutMillis, easing = KlasMotion.EmphasizedDecelerate), ArrivingScale)
        ) togetherWith fadeOut(tween(OutMillis, easing = KlasMotion.EmphasizedAccelerate))

/** Chrome drawn over the screens — a phone's tab bar — arriving on [fadeThrough]'s beat, with its screen. */
fun chromeEnter(): EnterTransition =
    fadeIn(tween(InMillis, delayMillis = OutMillis, easing = KlasMotion.EmphasizedDecelerate))

/** That chrome leaving with the screen it belongs to, as [fadeThrough] takes it away. */
fun chromeExit(): ExitTransition = fadeOut(tween(OutMillis, easing = KlasMotion.EmphasizedAccelerate))

/**
 * Back as the gesture draws it — Material's in-app predictive back, as `Modifier.predictiveBack` draws a step
 * inside one screen. The leaving screen stays **above** the one beneath, shrinks toward 90% and drifts from
 * the edge the swipe began at; only past the point of no return does it fade, uncovering the screen it leaves.
 *
 * The gesture seeks this transition, its progress eased as Material's components ease it.
 *
 * @param shiftInset Material's 8 dp taken off the drift, in pixels.
 */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.predictiveBackPop(
    swipeEdge: Int,
    shiftInset: Int,
): ContentTransform {
    val direction = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
    val pull = tween<Float>(PopMillis, easing = KlasMotion.PredictiveBack)
    return ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = scaleOut(pull, LeavingScale) +
            slideOut(tween(PopMillis, easing = KlasMotion.PredictiveBack)) { size ->
                IntOffset(direction * (size.width / ShiftDivisor - shiftInset).coerceAtLeast(0), 0)
            } +
            fadeOut(tween(PopFadeMillis, delayMillis = PopMillis - PopFadeMillis, easing = LinearEasing)),
        targetContentZIndex = -1f,
    )
}

/**
 * The end of [predictiveBackPop] for back that `NavDisplay` does not draw (`Modifier.predictiveBack`): once
 * the gesture lets go, the content beneath is there at once and the leaving one fades from where the finger
 * left it, above it.
 */
fun <S> AnimatedContentTransitionScope<S>.predictiveBackRelease(): ContentTransform = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = fadeOut(tween(PopFadeMillis, easing = LinearEasing)),
    targetContentZIndex = -1f,
)

private const val OutMillis = 90
private const val InMillis = 210
private const val ArrivingScale = 0.92f

private const val PopMillis = 300
private const val PopFadeMillis = 90
private const val LeavingScale = 0.9f

// Material's in-app shift: a twentieth of the width, less 8 dp.
private const val ShiftDivisor = 20
