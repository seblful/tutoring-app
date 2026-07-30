package com.seblful.klas.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * The two kinds of destination this app has, and the motion each one is entered with.
 *
 * Every screen used to arrive with `NavHost`'s default cross-fade, which says nothing about *where* it
 * came from. That matters most where the answer differs: opening a student's card is a step deeper into
 * the app and swiping back should undo it, while moving from «Люди» to «Отчёты» is a step sideways
 * between peers. One animation for both left the app with no way to tell a push from a tab switch, which
 * is the question "did that open or replace what I was looking at?" — and it is also what the
 * predictive-back gesture needs an answer to in order to preview anything (see the manifest's
 * `enableOnBackInvokedCallback`).
 *
 * Written as two builder functions rather than as transition arguments spelled at each `composable`,
 * because there are four of those arguments and sixteen screens: the version where each screen states
 * its own motion is the version where the fifth screen states it differently.
 */

/**
 * A **tab**: one of the four top-level peers. Cross-fades, with no direction, because there is no
 * hierarchy between them to describe — a slide would assert an order the bottom bar does not have.
 */
fun NavGraphBuilder.tab(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    enterTransition = { fadeIn(tween(TabMillis)) },
    exitTransition = { fadeOut(tween(TabMillis)) },
    popEnterTransition = { fadeIn(tween(TabMillis)) },
    popExitTransition = { fadeOut(tween(TabMillis)) },
    content = content,
)

/**
 * A **pushed** screen: a card, a form, a settings page. Slides in from the trailing edge and leaves back
 * towards it, so the gesture that dismisses it visibly reverses the gesture that opened it.
 *
 * The travel is a fraction of the width rather than the whole of it ([SlideFraction]): a full-width
 * slide reads as a page turn and costs the eye the whole distance, while a short one plus a fade lands
 * as depth. The outgoing screen underneath moves the other way by the same fraction, which is what makes
 * the two look like a stack instead of two independent animations.
 */
fun NavGraphBuilder.pushed(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = {
        slideInHorizontally(tween(EnterMillis)) { width -> width / SlideFraction } +
            fadeIn(tween(EnterMillis))
    },
    exitTransition = {
        slideOutHorizontally(tween(EnterMillis)) { width -> -width / SlideFraction } +
            fadeOut(tween(ExitMillis))
    },
    popEnterTransition = {
        slideInHorizontally(tween(EnterMillis)) { width -> -width / SlideFraction } +
            fadeIn(tween(EnterMillis))
    },
    popExitTransition = {
        slideOutHorizontally(tween(EnterMillis)) { width -> width / SlideFraction } +
            fadeOut(tween(ExitMillis))
    },
    content = content,
)

/** Long enough to be read as a direction, short enough not to be waited on. */
private const val EnterMillis = 260

/** The outgoing screen leaves faster than the incoming one arrives, so the new one leads. */
private const val ExitMillis = 200

/** A peer replacing a peer: no travel, so only the crossfade's own length. */
private const val TabMillis = 180

/** A quarter of the width — enough to carry direction, not far enough to read as a page turn. */
private const val SlideFraction = 4
