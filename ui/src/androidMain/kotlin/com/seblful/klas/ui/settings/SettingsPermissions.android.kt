package com.seblful.klas.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.seblful.klas.PermissionPrompt
import com.seblful.klas.notifications.rememberNotificationPermissionRequest
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.launchExternally
import com.seblful.klas.ui.common.rememberRejectFeedback

@Composable
internal actual fun rememberOpenSystemSettings(messages: Messages, page: SystemSettingsPage): () -> Unit {
    val context = LocalContext.current
    val missing = stringResource(Strings.settings_page_missing)
    val intent: (Context) -> Intent = when (page) {
        SystemSettingsPage.AppNotifications -> ::appNotificationSettings
        SystemSettingsPage.AppDetails -> ::appDetailsSettings
        SystemSettingsPage.ExactAlarms -> ::exactAlarmSettings
    }
    return { launchExternally(context, intent(context), missing, messages) }
}

internal fun appNotificationSettings(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        appDetailsSettings(context)
    }

internal fun appDetailsSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

@SuppressLint("InlinedApi")
internal fun exactAlarmSettings(context: Context): Intent =
    Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null),
    )
