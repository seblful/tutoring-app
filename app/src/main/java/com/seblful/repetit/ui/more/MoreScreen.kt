package com.seblful.repetit.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.repetit.R
import com.seblful.repetit.ui.common.constrainedContent

/**
 * The overflow section: everything that doesn't earn a tab.
 *
 * @param onGroups null while «Группы» has a tab of its own — the entry point moves here only when
 *   that tab is switched off in Settings, so the section is listed in exactly one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onSubjects: () -> Unit,
    onGroups: (() -> Unit)?,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).constrainedContent()) {
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                headlineContent = { Text(stringResource(R.string.more_subjects)) },
                modifier = Modifier.clickable { onSubjects() },
            )
            if (onGroups != null) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Groups, null) },
                    headlineContent = { Text(stringResource(R.string.more_groups)) },
                    supportingContent = { Text(stringResource(R.string.more_groups_desc)) },
                    modifier = Modifier.clickable(onClick = onGroups),
                )
            }
            ListItem(
                leadingContent = { Icon(Icons.Filled.Settings, null) },
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                modifier = Modifier.clickable { onSettings() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Save, null) },
                headlineContent = { Text(stringResource(R.string.more_backup)) },
                modifier = Modifier.clickable { onBackup() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.BugReport, null) },
                headlineContent = { Text(stringResource(R.string.diagnostics_title)) },
                modifier = Modifier.clickable { onDiagnostics() },
            )
        }
    }
}
