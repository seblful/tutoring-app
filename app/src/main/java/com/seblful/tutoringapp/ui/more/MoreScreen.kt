package com.seblful.tutoringapp.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
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
import com.seblful.tutoringapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onSubjects: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                leadingContent = { Icon(Icons.Filled.MenuBook, null) },
                headlineContent = { Text(stringResource(R.string.more_subjects)) },
                modifier = Modifier.clickable { onSubjects() },
            )
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
        }
    }
}
