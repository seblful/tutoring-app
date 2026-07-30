package com.seblful.klas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.common.KlasScreen

/**
 * The overflow section: everything that doesn't earn a tab.
 *
 * Four entries, all of them things a tutor actually goes looking for. «Группы» has left (it is a view
 * inside «Люди» now, not a place), and «Диагностика» has moved to the foot of Settings — it is
 * developer furniture and had no business standing as a peer of «Настройки» in the app's main menu.
 */
@Composable
fun MoreScreen(
    onSubjects: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    KlasScreen(title = stringResource(R.string.nav_more)) { modifier ->
        Column(modifier) {
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                headlineContent = { Text(stringResource(R.string.more_subjects)) },
                modifier = Modifier.clickable { onSubjects() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Settings, null) },
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                modifier = Modifier.clickable { onSettings() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Save, null) },
                headlineContent = { Text(stringResource(R.string.more_backup)) },
                modifier = Modifier.clickable { onBackup() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                headlineContent = { Text(stringResource(R.string.about_title)) },
                modifier = Modifier.clickable { onAbout() },
            )
        }
    }
}
