package com.seblful.klas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.common.IconAvatar
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.theme.KlasIcons

/** The overflow section: everything that doesn't earn a tab. */
@Composable
fun MoreScreen(
    onSubjects: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    KlasScreen(title = stringResource(R.string.nav_more)) { modifier ->
        // No wordmark above the menu: the bar already names the screen, and a logo between the title and
        // the first row pushed the four things a tutor came here for halfway down the page. The brand has
        // its place in «О приложении», one row below.
        // On one card, led by avatars, as Settings' own index is: the two menus are one kind of list.
        Column(modifier.verticalScroll(rememberScrollState()).padding(Spacing.gutter)) {
            SectionCard {
                Column(Modifier.padding(vertical = Spacing.inline)) {
                    NavigationRow(KlasIcons.Outlined.MenuBook, stringResource(R.string.more_subjects), onSubjects)
                    NavigationRow(KlasIcons.Outlined.Settings, stringResource(R.string.settings_title), onSettings)
                    NavigationRow(KlasIcons.Outlined.Save, stringResource(R.string.more_backup), onBackup)
                    NavigationRow(KlasIcons.Outlined.Info, stringResource(R.string.about_title), onAbout)
                }
            }
        }
    }
}

@Composable
private fun NavigationRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { IconAvatar(icon) },
        headlineContent = { Text(label) },
        trailingContent = { OpensScreenMark() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) },
    )
}
