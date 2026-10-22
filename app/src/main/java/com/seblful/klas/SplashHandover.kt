package com.seblful.klas

import android.view.View
import android.view.animation.PathInterpolator

/**
 * Lifts the launch splash by Material's fade through, the transition every destination already arrives by:
 * the mark leaves, the splash fades, and the app grows in from 92% beneath it, 300 ms in all.
 *
 * It starts once the splash's keep condition lets go, so nothing waits on it; with animations turned off
 * it ends at once. [onEnd] must remove the splash: until then it covers the app and takes its touches.
 */
internal fun fadeThrough(splash: View, icon: View, content: View, onEnd: () -> Unit) {
    content.scaleX = ContentStartScale
    content.scaleY = ContentStartScale
    content.animate().scaleX(1f).scaleY(1f)
        .setStartDelay(IconLeadMillis).setDuration(ContentGrowMillis).setInterpolator(EmphasizedDecelerate)
    icon.animate().alpha(0f).scaleX(IconEndScale).scaleY(IconEndScale)
        .setDuration(IconLeaveMillis).setInterpolator(EmphasizedAccelerate)
    splash.animate().alpha(0f)
        .setStartDelay(IconLeadMillis).setDuration(SplashFadeMillis).setInterpolator(Standard)
        .withEndAction(onEnd)
}

private const val ContentStartScale = 0.92f
private const val IconEndScale = 0.9f

// The mark starts leaving first; the splash and the app follow once it is under way.
private const val IconLeadMillis = 60L
private const val IconLeaveMillis = 120L
private const val SplashFadeMillis = 180L
private const val ContentGrowMillis = 240L

// KlasMotion's easings, as View interpolators.
private val EmphasizedDecelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
private val Standard = PathInterpolator(0.2f, 0f, 0f, 1f)
