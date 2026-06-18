package com.seblful.tutoringapp.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.ui.common.EmptyState
import com.seblful.tutoringapp.ui.common.StudentEditDialog
import com.seblful.tutoringapp.ui.common.balanceColor
import com.seblful.tutoringapp.ui.common.balanceDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    onStudentClick: (Long) -> Unit,
    viewModel: StudentsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    var adding by remember { mutableStateOf(false) }

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
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (rows.isEmpty()) {
                EmptyState(stringResource(R.string.students_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.student.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.student.name) },
                            supportingContent = {
                                Text(
                                    text = balanceDescription(row.balance),
                                    color = balanceColor(row.balance),
                                )
                            },
                            trailingContent = if (row.student.archived) {
                                { Text(stringResource(R.string.student_archived_badge), fontStyle = FontStyle.Italic) }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStudentClick(row.student.id) },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        StudentEditDialog(
            title = stringResource(R.string.student_add_title),
            initialName = "",
            initialPhone = "+375 ",
            initialNote = "",
            initialMode = ClassMode.IN_PERSON,
            onConfirm = { name, phone, note, mode -> viewModel.addStudent(name, phone, note, mode) },
            onDismiss = { adding = false },
        )
    }
}
