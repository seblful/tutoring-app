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

    /** elegram by phone number, through the public `t.me` deep link. */
    data class Telegram(val phone: String) : ExternalTarget

    /** [text] into the phone's share sheet, titled [chooserTitle]. */
    data class ShareText(val text: String, val chooserTitle: String) : ExternalTarget
}

/** The dialler's address for [phone]: spaces out, the rest as typed. */
fun dialUri(phone: String): String = "tel:${phone.filterNot { it.isWhitespace() }}"

fun mailUri(address: String): String = "mailto:$address"

/** See [ExternalTarget.Telegram] for why `t.me` and why digits only. */
fun telegramUri(phone: String): String = "https://t.me/+${phone.filter { it.isDigit() }}"

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
 */
fun launchExternally(
    context: Context,
    intent: Intent,
    failureMessage: String,
    messages: Messages,
) {
    runCatching { context.startActivity(intent) }.onFailure { messages.show(failureMessage) }
}
