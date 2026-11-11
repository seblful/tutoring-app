package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.theme.KlasIcons

/**
 * The last action on every tab's bar: Settings, which holds the subjects, the backup and the app's own pages.
 * The same place on every tab, so it is found once.
 */
@Composable
fun SettingsGear(onClick: () -> Unit) {
    NamedIconButton(KlasIcons.Outlined.Settings, stringResource(Strings.settings_title), onClick = onClick)
}
