package com.seblful.klas.ui.common

import android.content.Context
import android.content.Intent

/**
 * Hands something off to whichever app the platform says handles it — **guarded**, because on a real
 * device any of them can be missing.
 *
 * A tablet with no dialler, a phone with no mail client, Telegram not installed, an OEM build with no
 * activity behind an `ACTION_*_SETTINGS` action: each of those raises `ActivityNotFoundException`, and an
 * unhandled one is a crash in the middle of an ordinary tap. It says so in the screen's own snackbar
 * instead.
 *
 * Here in `ui/common/` rather than private to a screen because two screens now reach outside the app —
 * the student card's phone, email and Telegram actions, and the settings screen's way into the system
 * notification page. The same argument that moved `AddRow`: the second caller must not have to re-derive
 * the guard, since the whole point of it is the case nobody remembers to test.
 *
 * A screen-level function and not a view model's: it needs a `Context`, and a view model in this app
 * touches no `Context`, `Intent` or `Uri` (see `storage/`).
 */
fun launchExternally(
    context: Context,
    intent: Intent,
    failureMessage: String,
    messages: Messages,
) {
    runCatching { context.startActivity(intent) }.onFailure { messages.show(failureMessage) }
}
