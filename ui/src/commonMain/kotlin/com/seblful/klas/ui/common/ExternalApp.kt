package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Something the tutor tapped that another app does — said as *what*, not as an Android `Intent`, so the
 * screens asking for it name nothing Android-only. Which app, and how it is asked, is the platform's.
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

fun interface ExternalApps {
    fun open(target: ExternalTarget, failureMessage: String)
}

/** [ExternalApps] for a screen, saying a failure in [messages]' snackbar. */
@Composable
expect fun rememberExternalApps(messages: Messages): ExternalApps
