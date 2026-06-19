package com.seblful.tutoringapp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.ui.common.EmptyState
import com.seblful.tutoringapp.ui.common.TextFieldDialog
import com.seblful.tutoringapp.ui.students.StudentFormContent
import com.seblful.tutoringapp.ui.students.StudentFormViewModel

private enum class Step { SUBJECTS, STUDENT }

/**
 * First-run setup. Step 1: add subjects. Step 2: add the first student (with subjects + prices).
 * Finishing marks onboarding complete; [onFinished] lets the host refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var step by remember { mutableStateOf(Step.SUBJECTS) }
    val subjects by viewModel.subjects.collectAsState()
    var addingSubject by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (step == Step.SUBJECTS) R.string.onboarding_subjects_title
                            else R.string.onboarding_student_title
                        )
                    )
                },
                navigationIcon = {
                    if (step == Step.STUDENT) {
                        IconButton(onClick = { step = Step.SUBJECTS }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                Step.SUBJECTS -> {
                    Text(
                        stringResource(R.string.onboarding_subjects_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    OutlinedButton(
                        onClick = { addingSubject = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.subject_add_title), Modifier.padding(start = 8.dp))
                    }
                    if (subjects.isEmpty()) {
                        EmptyState(stringResource(R.string.subjects_empty), Modifier.weight(1f))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(subjects, key = { it.id }) { subject ->
                                ListItem(headlineContent = { Text(subject.name) })
                            }
                        }
                    }
                    Button(
                        onClick = { step = Step.STUDENT },
                        enabled = subjects.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(stringResource(R.string.onboarding_continue)) }
                }

                Step.STUDENT -> {
                    val studentVm: StudentFormViewModel = hiltViewModel()
                    val studentState by studentVm.state.collectAsState()
                    StudentFormContent(
                        state = studentState,
                        viewModel = studentVm,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    Button(
                        onClick = { studentVm.save { viewModel.complete(); onFinished() } },
                        enabled = studentState.isValid,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(stringResource(R.string.onboarding_finish)) }
                }
            }
        }
    }

    if (addingSubject) {
        TextFieldDialog(
            title = stringResource(R.string.subject_add_title),
            label = stringResource(R.string.subject_name_label),
            initial = "",
            onConfirm = { viewModel.addSubject(it) },
            onDismiss = { addingSubject = false },
        )
    }
}
