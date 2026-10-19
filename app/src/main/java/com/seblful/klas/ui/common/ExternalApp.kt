package com.seblful.klas.ui.common

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/**
 * Something the tutor tapped that another app does — said as *what*, not as an Android `Intent`, so the
 * screens asking for it name nothing Android-only. Which app, and how it is asked, is `toIntent`'s.
 */
sealed interface ExternalTarget {

    /** The dialler with [phone] filled in, not yet called. */
    data class Dial(val phone: String) : ExternalTarget

    /** A new mail to [address]. */
    data class Email(val address: String) : ExternalTarget

    /**
     * Telegram by phone number, through the public `t.me` deep link.
     *
     * `https://t.me/+<number>` opens the app when it is installed and the browser when it is not, which
     * is the right behaviour either way — the alternative, an app-specific `tg://` scheme, fails hard on
     * a phone without Telegram. The number is stripped to digits and a leading `+`: `t.me` takes it in
     * international form, and the tutor types it however they like (the form pre-fills «+375 »).
     */
    data class Telegram(val phone: String) : ExternalTarget

    /** [text] into the phone's share sheet, titled [chooserTitle]. */
    data class ShareText(val text: String, val chooserTitle: String) : ExternalTarget
}

/** The dialler's address for [phone]: spaces out, the rest as typed. */
fun dialUri(phone: String): String = "tel:${phone.filterNot { it.isWhitespace() }}"

fun mailUri(address: String): String = "mailto:$address"

/** See [ExternalTarget.Telegram] for why `t.me` and why digits only. */
fun telegramUri(phone: String): String = "https://t.me/+${phone.filter { it.isDigit() }}"

/** How Android is asked for [this]: the dialler and a new mail by their own actions, not a generic view. */
internal fun ExternalTarget.toIntent(): Intent = when (this) {
    is ExternalTarget.Dial -> Intent(Intent.ACTION_DIAL, dialUri(phone).toUri())
    is ExternalTarget.Email -> Intent(Intent.ACTION_SENDTO, mailUri(address).toUri())
    is ExternalTarget.Telegram -> Intent(Intent.ACTION_VIEW, telegramUri(phone).toUri())
    is ExternalTarget.ShareText -> Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
        chooserTitle,
    )
}

/** Opens [ExternalTarget]s from a screen, saying the failure message in its snackbar when no app can. */
fun interface ExternalApps {
    fun open(target: ExternalTarget, failureMessage: String)
}

@Composable
fun rememberExternalApps(messages: Messages): ExternalApps {
    val context = LocalContext.current
    return remember(context, messages) {
        ExternalApps { target, failureMessage ->
            launchExternally(context, target.toIntent(), failureMessage, messages)
        }
    }
}

/**
 * Hands something off to whichever app the platform says handles it — **guarded**, because on a real
 * device any of them can be missing.
 *
 * A tablet with no dialler, a phone with no mail client, Telegram not installed, an OEM build with no
 * activity behind an `ACTION_*_SETTINGS` action: each of those raises `ActivityNotFoundException`, and an
 * unhandled one is a crash in the middle of an ordinary tap. It says so in the screen's own snackbar
 * instead.
 *
 * Called directly only by the settings screen, for the system's own settings pages, which exist on Android
 * alone; everything else goes through [rememberExternalApps].
 */
fun launchExternally(
    context: Context,
    intent: Intent,
    failureMessage: String,
    messages: Messages,
) {
    runCatching { context.startActivity(intent) }.onFailure { messages.show(failureMessage) }
}
