package com.seblful.klas.testutil

import android.content.Context
import android.content.res.Resources
import com.seblful.klas.text.Texts

/** The words a screen without a provider reads here: this context's configuration language. */
fun Context.texts(): Texts = resources.texts()

/** The words of this configuration's language, as `stringResource` reads them in a test. */
fun Resources.texts(): Texts = Texts.of(configuration.locales[0].toLanguageTag())
