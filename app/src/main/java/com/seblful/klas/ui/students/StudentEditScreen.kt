package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.ui.common.FormGap
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LeavableForm
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.StillNeeded
import com.seblful.klas.ui.common.leaveGuard
import com.seblful.klas.ui.designsystem.component.FormSaveBar

/**
 * Correcting a student's name, contacts, note and usual place: a screen of its own, saved from the bar
 * every form saves from (D5) — five fields were the tallest dialog in the app. Subjects and rates are
 * not here: they are edited where they are listed, on the profile, one row each.
 */
@Composable
fun StudentEditScreen(onBack: () -> Unit, viewModel: StudentDetailViewModel) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val student = overview?.details?.student
    if (student == null) {
        LoadingDetailScreen(onBack)
        return
    }
    StudentEditForm(
        student = student,
        onSave = { name, phone, email, note, mode ->
            viewModel.editStudent(name, phone, email, note, mode)
            onBack()
        },
        onBack = onBack,
    )
}

/** The fields as typed, against the [student] they were opened on — what leaving would lose. */
private class EditedStudent(
    val name: String,
    val phone: String,
    val email: String,
    val note: String,
    val mode: ClassMode,
    private val opened: List<Any>,
) : LeavableForm {
    override val isDirty: Boolean get() = listOf(name, phone, email, note, mode) != opened
    override val canSave: Boolean get() = name.isNotBlank()
}

@Composable
private fun StudentEditForm(
    student: Student,
    onSave: (name: String, phone: String, email: String, note: String, mode: ClassMode) -> Unit,
    onBack: () -> Unit,
) {
    val openedPhone = Phone.editable(student.phone, LocalCurrency.current)
    var name by rememberSaveable { mutableStateOf(student.name) }
    var phone by rememberSaveable { mutableStateOf(openedPhone) }
    var email by rememberSaveable { mutableStateOf(student.email) }
    var note by rememberSaveable { mutableStateOf(student.note) }
    var mode by rememberSaveable { mutableStateOf(student.defaultMode) }
    val form = EditedStudent(
        name, phone, email, note, mode,
        opened = listOf(student.name, openedPhone, student.email, student.note, student.defaultMode),
    )
    fun save() = onSave(name.trim(), phone.trim(), email.trim(), note.trim(), mode)
    val requestLeave = leaveGuard(form = form, onLeave = onBack, onSave = ::save)

    KlasScreen(
        title = stringResource(R.string.student_edit_title),
        onBack = requestLeave,
        imeAware = true,
    ) { modifier, bottomInset ->
        Column(modifier) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.inline),
            ) {
                StudentContactFields(
                    name = name,
                    onName = { name = it },
                    phone = phone,
                    onPhone = { phone = it },
                    email = email,
                    onEmail = { email = it },
                    note = note,
                    onNote = { note = it },
                    mode = mode,
                    onMode = { mode = it },
                )
            }
            FormSaveBar(stringResource(R.string.action_save), form.canSave, ::save, bottomInset) {
                StillNeeded(listOfNotNull(FormGap.NAME.takeIf { name.isBlank() }))
            }
        }
    }
}
