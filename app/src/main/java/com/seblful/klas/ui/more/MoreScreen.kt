package com.seblful.klas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.common.KlasScreen

/**
 * The overflow section: everything that doesn't earn a tab.
 *
 * Four entries, all of them things a tutor actually goes looking for. «Группы» has left (it is a view
 * inside «Люди» now, not a place), and «Диагностика» has moved to the foot of Settings — it is
 * developer furniture and had no business standing as a peer of «Настройки» in the app's main menu.
 *
 * Every row **goes somewhere**, and now says so. Four rows with a leading icon and nothing trailing
 * read as a settings list — four things to *set* — which is the one thing none of them is: each opens
 * a screen. The chevron is the platform's word for that, and it is the only mark that tells these
 * apart from the switch rows two taps away in Settings.
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
            NavigationRow(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.more_subjects), onSubjects)
            NavigationRow(Icons.Outlined.Settings, stringResource(R.string.settings_title), onSettings)
            NavigationRow(Icons.Outlined.Save, stringResource(R.string.more_backup), onBackup)
            NavigationRow(Icons.Outlined.Info, stringResource(R.string.about_title), onAbout)
        }
    }
}

/**
 * One entry of the menu: an outlined glyph for what it is, the name, and a chevron saying it opens.
 *
 * The chevron is **filled**, as the app's chevrons are (see *Icons* in `docs/ui-conventions.md`): the
 * leading glyph is art for the row, the trailing one is a statement about what tapping does.
 */
@Composable
private fun NavigationRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
        trailingContent = {
            Icon(
                Icons.Filled.ChevronRight,
                // Decorative: the row is one tap target and the headline already names where it goes,
                // so a description here would have a screen reader read every row twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
