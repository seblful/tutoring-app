package com.seblful.klas.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * **Every destination arrives the same way: a short cross-fade.** One rule, one duration, no direction.
 *
 * There used to be two builders. A *tab* cross-faded between peers and a *pushed* screen slid a quarter
 * of the width, so that back visibly reversed the way in — the argument being that the app should say
 * whether a screen opened on top of what you were looking at or replaced it.
 *
 * It was not worth what it cost. The slide had to be short to avoid reading as a page turn, a short slide
 * needs a fade or an opaque screen appears from nowhere already covering most of the display, and a fade
 * over the travel means neither screen is opaque while both are on screen — so the compositor drew one
 * through the other. Tapping «+» on the schedule printed «Расписание» and «Новое занятие» over each
 * other for a quarter of a second, with the day's lessons showing through the form's fields. Tuning the
 * two fades against each other made that shorter without making it a different kind of thing, and the
 * whole apparatus existed to convey one bit — deeper, or sideways — that the app bar's back arrow already
 * states in words, permanently, on every screen that has one.
 *
 * So the distinction is gone rather than tuned. Motion here says only "the screen changed", which is the
 * one thing it is reliably good at saying.
 *
 * **Predictive back still works** — the manifest keeps `enableOnBackInvokedCallback` — it simply previews
 * a fade rather than a slide.
 */
fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { fadeIn(tween(FadeMillis)) },
    exitTransition = { fadeOut(tween(FadeMillis)) },
    popEnterTransition = { fadeIn(tween(FadeMillis)) },
    popExitTransition = { fadeOut(tween(FadeMillis)) },
    content = content,
)

/** Long enough to register as a change, short enough that nothing is waited on. */
private const val FadeMillis = 180
