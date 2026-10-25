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
     * The system's own chooser of apps to write to [phone] with: SMS apps, and — when [international] is
     * known — Telegram and WhatsApp, whichever are installed.
     */
    data class Message(val phone: String, val international: String?, val chooserTitle: String) : ExternalTarget

    /** [text] into the phone's share sheet, titled [chooserTitle]. */
    data class ShareText(val text: String, val chooserTitle: String) : ExternalTarget
}

/** The dialler's address for [phone]: spaces out, the rest as typed. */
fun dialUri(phone: String): String = "tel:${phone.filterNot { it.isWhitespace() }}"

/** Text messages to [phone], spaces out. */
fun smsUri(phone: String): String = "smsto:${phone.filterNot { it.isWhitespace() }}"

fun mailUri(address: String): String = "mailto:$address"

/** Telegram by number through the public `t.me` link, which wants digits only. */
fun telegramUri(phone: String): String = "https://t.me/+${phone.filter { it.isDigit() }}"

/** WhatsApp's click-to-chat link, which wants the whole number in digits. */
fun whatsAppUri(phone: String): String = "https://wa.me/${phone.filter { it.isDigit() }}"

private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

internal fun ExternalTarget.toIntent(): Intent = when (this) {
    is ExternalTarget.Dial -> Intent(Intent.ACTION_DIAL, dialUri(phone).toUri())
    is ExternalTarget.Email -> Intent(Intent.ACTION_SENDTO, mailUri(address).toUri())
    is ExternalTarget.Message -> messageChooser()
    is ExternalTarget.ShareText -> Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
        chooserTitle,
    )
}

/**
 * One system chooser, not a menu of our own: text messages are its base, and the messengers are added by
 * package so the chooser lists those installed and drops the rest.
 */
private fun ExternalTarget.Message.messageChooser(): Intent {
    val messengers = international?.let { number ->
        listOf(Intent(Intent.ACTION_VIEW, telegramUri(number).toUri()).setPackage(TELEGRAM_PACKAGE)) +
            WHATSAPP_PACKAGES.map { Intent(Intent.ACTION_VIEW, whatsAppUri(number).toUri()).setPackage(it) }
    }.orEmpty()
    return Intent.createChooser(Intent(Intent.ACTION_SENDTO, smsUri(international ?: phone).toUri()), chooserTitle)
        .putExtra(Intent.EXTRA_INITIAL_INTENTS, messengers.toTypedArray())
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
