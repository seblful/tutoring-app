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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.seblful.klas.R
import com.seblful.klas.notifications.rememberNotificationPermissionRequest
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.launchExternally
import com.seblful.klas.ui.common.rememberRejectFeedback

@Composable
internal fun RefreshSettingsPermissions(viewModel: SettingsViewModel) {
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refreshPermissions() }
    }
}

@Composable
internal fun rememberOpenSystemSettings(messages: Messages, intent: (Context) -> Intent): () -> Unit {
    val context = LocalContext.current
    val missing = stringResource(R.string.settings_page_missing)
    return { launchExternally(context, intent(context), missing, messages) }
}

@Composable
internal fun rememberNotificationAction(
    viewModel: SettingsViewModel,
    messages: Messages,
): (NotificationsIntent) -> Unit {
    val denied = stringResource(R.string.settings_notifications_denied)
    val openLabel = stringResource(R.string.action_open_settings)
    val openSettings = rememberOpenSystemSettings(messages, ::appNotificationSettings)
    val reject = rememberRejectFeedback()
    val request = rememberNotificationPermissionRequest { granted ->
        viewModel.refreshPermissions()
        if (!granted) {
            reject()
            messages.show(denied, openLabel, openSettings)
        }
    }
    return { intent ->
        when (intent) {
            NotificationsIntent.Request -> request()
            NotificationsIntent.BlockedBySystem -> openSettings()
            NotificationsIntent.Nothing -> Unit
        }
    }
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
