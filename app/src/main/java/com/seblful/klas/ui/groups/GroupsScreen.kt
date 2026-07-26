package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.text

/**
 * The group roster list: who is taught together, and a way in to edit or create a group.
 *
 * @param onBack null when this is a bottom-bar tab (a tab has nothing to go back to); non-null when
 *   the screen was opened from «Ещё», which is where it lives while the tab is switched off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: (() -> Unit)?,
    onGroupClick: (Long) -> Unit,
    onAddGroup: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleArchived() }) {
                        Icon(
                            Icons.Filled.Inventory2,
                            stringResource(R.string.groups_show_archived),
                            tint = if (showArchived) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGroup) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (rows.isEmpty()) {
                EmptyState(stringResource(R.string.groups_empty))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().constrainedContent(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.group.id }) { row ->
                        ContentCard(onClick = { onGroupClick(row.group.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.group.name, style = MaterialTheme.typography.titleMedium)
                                    // What the group is taught, when it says. A group from before the
                                    // subject existed shows nothing here rather than a placeholder —
                                    // it genuinely has no answer, and inventing one would be a lie.
                                    row.subjectName?.let { subject ->
                                        Text(
                                            subject,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = row.memberNames.joinToString(", ")
                                            .ifBlank { stringResource(R.string.group_no_members) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    // The rate belongs on the row now that the group carries it: it is
                                    // what every one of its lessons bills each member.
                                    Text(
                                        stringResource(R.string.price_per_hour, row.group.price.text()),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (row.group.archived) {
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.student_archived_badge),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
