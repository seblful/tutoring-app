package com.seblful.klas.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The language a composition speaks. The app root provides the tutor's choice; without a provider (a
 * preview, a test) the platform's own language is read, as Android's resources would.
 */
val LocalTexts = staticCompositionLocalOf<Texts?> { null }

/** The texts in effect here. */
@Composable
@ReadOnlyComposable
fun currentTexts(): Texts = LocalTexts.current ?: platformTexts()

@Composable
@ReadOnlyComposable
fun stringResource(resource: StringResource): String = currentTexts().string(resource)

@Composable
@ReadOnlyComposable
fun stringResource(resource: StringResource, vararg formatArgs: Any?): String =
    currentTexts().string(resource, *formatArgs)

@Composable
@ReadOnlyComposable
fun pluralStringResource(resource: PluralResource, count: Int): String = currentTexts().plural(resource, count)

@Composable
@ReadOnlyComposable
fun pluralStringResource(resource: PluralResource, count: Int, vararg formatArgs: Any?): String =
    currentTexts().plural(resource, count, *formatArgs)

@Composable
@ReadOnlyComposable
fun stringArrayResource(resource: StringArrayResource): List<String> = currentTexts().array(resource)

/** The platform's language where nothing provided one: the configuration's on Android, the app's on iOS. */
@Composable
@ReadOnlyComposable
internal expect fun platformTexts(): Texts
