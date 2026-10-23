package com.seblful.klas.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.Privacy
import com.seblful.klas.lock.rememberLockAvailable
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun PrivacySettingsScreen(onBack: (() -> Unit)?, viewModel: SettingsViewModel = metroViewModel()) {
    val lockAvailable = rememberLockAvailable()
    SettingsPage(R.string.settings_privacy_title, onBack, viewModel) { page, modifier, bottomInset, _ ->
        PrivacySettingsContent(page.settings, viewModel::update, lockAvailable, modifier, bottomInset)
    }
}

/**
 * @param lockAvailable whether the phone has biometrics or a screen lock — without one the app lock could
 * only lock the tutor out, so it is offered only with one.
 */
@Composable
internal fun PrivacySettingsContent(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    lockAvailable: Boolean,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
) {
    val privacy = settings.privacy
    val change: (Privacy.() -> Privacy) -> Unit = { edit -> onUpdate { it.copy(privacy = it.privacy.edit()) } }
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.gutter).padding(bottomInset)) {
        SettingsGroup(stringResource(R.string.settings_section_lock_screen)) {
            SwitchRow(
                title = stringResource(R.string.privacy_lock_screen_details),
                description = stringResource(R.string.privacy_lock_screen_details_desc),
                checked = privacy.lockScreenDetails,
                onCheckedChange = { on -> change { copy(lockScreenDetails = on) } },
            )
            SwitchRow(
                title = stringResource(R.string.privacy_lock_screen_widgets),
                description = stringResource(R.string.privacy_lock_screen_widgets_desc),
                checked = privacy.lockScreenWidgets,
                onCheckedChange = { on -> change { copy(lockScreenWidgets = on) } },
            )
        }
        SettingsGroup(stringResource(R.string.settings_section_this_app)) {
            SwitchRow(
                title = stringResource(R.string.privacy_app_lock),
                description = stringResource(
                    if (lockAvailable) R.string.privacy_app_lock_desc else R.string.privacy_app_lock_unavailable,
                ),
                checked = privacy.appLock && lockAvailable,
                onCheckedChange = { on -> change { copy(appLock = on) } },
                enabled = lockAvailable,
            )
            if (privacy.appLock && lockAvailable) {
                ChoiceRow(
                    title = stringResource(R.string.privacy_relock),
                    options = Privacy.RELOCK_CHOICES.map { it to relockLabel(it) },
                    selected = privacy.relockAfterMinutes,
                    onSelect = { minutes -> change { copy(relockAfterMinutes = minutes) } },
                )
            }
            SwitchRow(
                title = stringResource(R.string.privacy_hide_recents),
                description = stringResource(
                    when {
                        privacy.appLock -> R.string.privacy_hide_recents_locked
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> R.string.privacy_hide_recents_desc
                        else -> R.string.privacy_hide_recents_desc_screenshots
                    },
                ),
                checked = privacy.hideInRecents || privacy.appLock,
                onCheckedChange = { on -> change { copy(hideInRecents = on) } },
                enabled = !privacy.appLock,
            )
        }
    }
}

@Composable
private fun relockLabel(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.privacy_relock_immediately) else durationLabel(minutes)

/** What the index says of this page: the strongest choice in force. */
@Composable
internal fun privacySummary(privacy: Privacy): String = stringResource(
    when {
        privacy.appLock -> R.string.privacy_summary_locked
        privacy.lockScreenDetails -> R.string.privacy_summary_details
        else -> R.string.privacy_summary_private
    },
)
