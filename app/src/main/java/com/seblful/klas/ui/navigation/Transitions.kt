package com.seblful.klas.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene

/** **Every destination arrives the same way: a short cross-fade.** One rule, one duration, no direction. */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.crossFade(): ContentTransform =
    fadeIn(tween(FadeMillis)) togetherWith fadeOut(tween(FadeMillis))

private const val FadeMillis = 180
