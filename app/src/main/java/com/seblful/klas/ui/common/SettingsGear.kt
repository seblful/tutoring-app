package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasIcons

/**
 * The last action on every tab's bar: Settings, which holds the subjects, the backup and the app's own pages
 * now that «Ещё» is not a tab. The same place on every tab, so it is found once.
 */
@Composable
fun SettingsGear(onClick: () -> Unit) {
    NamedIconButton(KlasIcons.Outlined.Settings, stringResource(R.string.settings_title), onClick = onClick)
}
