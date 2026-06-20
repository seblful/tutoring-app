package com.seblful.repetit.ui.students

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.ui.common.Avatar
import com.seblful.repetit.ui.common.ContentCard
import com.seblful.repetit.ui.common.EmptyState
import com.seblful.repetit.ui.common.balanceColor
import com.seblful.repetit.ui.common.balanceDescription
import com.seblful.repetit.ui.common.constrainedContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    onStudentClick: (Long) -> Unit,
    onAddStudent: () -> Unit,
    viewModel: StudentsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.students_title)) },
                actions = {
                    IconButton(onClick = { viewModel.toggleArchived() }) {
                        Icon(
                            Icons.Filled.Inventory2,
                            stringResource(R.string.students_show_archived),
                            tint = if (showArchived) androidx.compose.material3.MaterialTheme.colorScheme.primary
                            else androidx.compose.material3.LocalContentColor.current,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddStudent) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (rows.isEmpty()) {
                EmptyState(stringResource(R.string.students_empty))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().constrainedContent(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.student.id }) { row ->
                        ContentCard(onClick = { onStudentClick(row.student.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(row.student.name)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.student.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = balanceDescription(row.balance),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = balanceColor(row.balance),
                                    )
                                }
                                if (row.student.archived) {
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
