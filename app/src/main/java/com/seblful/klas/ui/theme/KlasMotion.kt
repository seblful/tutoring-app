package com.seblful.klas.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Material's standard motion scheme, and the easings its transitions keep.
 *
 * material3 1.4 holds `MotionScheme` internal, so its token values are restated here. Spatial springs move
 * and resize things, slightly underdamped; effects springs change colour and opacity, without overshoot.
 * Once `MaterialTheme.motionScheme` is public, these become reads of it.
 */
object KlasMotion {
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(SpatialDamping, stiffness = 700f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(SpatialDamping, stiffness = 300f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(EffectsDamping, stiffness = 1600f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(EffectsDamping, stiffness = 3800f)

    /** The expressive scheme's fast spatial spring: a visible bounce, kept for the app's pops. */
    fun <T> bouncySpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** For what enters the screen. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** For what leaves it. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** How a back gesture's progress becomes motion, as Material's own sheets and drawers follow it. */
    val PredictiveBack = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    private const val SpatialDamping = 0.9f
    private const val EffectsDamping = 1f
}
