package com.seblful.klas.ui.common

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

fun ExternalTarget.toIntent(): Intent = when (this) {
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
@Composable
actual fun rememberExternalApps(messages: Messages): ExternalApps {
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
