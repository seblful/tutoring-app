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
        .setStartDelay(60).setDuration(240).setInterpolator(EmphasizedDecelerate)
    icon.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f)
        .setDuration(120).setInterpolator(EmphasizedAccelerate)
    splash.animate().alpha(0f)
        .setStartDelay(60).setDuration(180).setInterpolator(Standard)
        .withEndAction(onEnd)
}

private const val ContentStartScale = 0.92f

// KlasMotion's easings, as View interpolators.
private val EmphasizedDecelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
private val Standard = PathInterpolator(0.2f, 0f, 0f, 1f)
