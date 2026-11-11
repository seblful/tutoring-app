package com.seblful.klas.ui.settings

import androidx.compose.runtime.Composable
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Messages
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.UIApplicationOpenSettingsURLString

/** iOS has one page per app in Settings, and a notifications page on it; alarms need no permission. */
@Composable
internal actual fun rememberOpenSystemSettings(messages: Messages, page: SystemSettingsPage): () -> Unit {
    val missing = stringResource(Strings.settings_page_missing)
    val address = when (page) {
        SystemSettingsPage.AppNotifications -> UIApplicationOpenNotificationSettingsURLString
        SystemSettingsPage.AppDetails, SystemSettingsPage.ExactAlarms -> UIApplicationOpenSettingsURLString
    }
    return {
        val url = NSURL.URLWithString(address)
        if (url == null) {
            messages.show(missing)
        } else {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>()) { opened ->
                if (!opened) messages.show(missing)
            }
        }
    }
}
