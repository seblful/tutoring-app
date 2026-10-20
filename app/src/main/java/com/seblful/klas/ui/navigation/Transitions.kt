package com.seblful.klas.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlin.reflect.KClass

/** *Every destination arrives the same way: a short cross-fade.** One rule, one duration, no direction. */
inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = screen(T::class, content)

/** [screen] for a route known only as a class — a tab's, which [TopDestination] holds as an instance. */
fun <T : Any> NavGraphBuilder.screen(
    route: KClass<T>,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    typeMap = emptyMap(),
    deepLinks = emptyList(),
    enterTransition = { fadeIn(tween(FadeMillis)) },
    exitTransition = { fadeOut(tween(FadeMillis)) },
    popEnterTransition = { fadeIn(tween(FadeMillis)) },
    popExitTransition = { fadeOut(tween(FadeMillis)) },
    content = content,
)

private const val FadeMillis = 180
