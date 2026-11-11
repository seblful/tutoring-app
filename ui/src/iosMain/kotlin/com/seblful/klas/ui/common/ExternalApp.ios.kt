package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberExternalApps(messages: Messages): ExternalApps = remember(messages) {
    ExternalApps { target, failureMessage ->
        when (target) {
            is ExternalTarget.Dial -> openUrl(dialUri(target.phone), failureMessage, messages)
            is ExternalTarget.Email -> openUrl(mailUri(target.address), failureMessage, messages)
            // iOS has no chooser across messengers: Messages, which also sends iMessage.
            is ExternalTarget.Message -> {
                val number = (target.international ?: target.phone).filterNot { it.isWhitespace() }
                openUrl("sms:$number", failureMessage, messages)
            }
            is ExternalTarget.ShareText -> {
                val sheet = UIActivityViewController(activityItems = listOf(target.text), applicationActivities = null)
                presentOnTop(sheet)
            }
        }
    }
}

private fun openUrl(address: String, failureMessage: String, messages: Messages) {
    val url = NSURL.URLWithString(address) ?: return messages.show(failureMessage)
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>()) { opened ->
        if (!opened) messages.show(failureMessage)
    }
}
