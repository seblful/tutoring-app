package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.DropdownField
import com.seblful.klas.ui.common.MemberPickRow
import com.seblful.klas.ui.common.MenuField
import com.seblful.klas.ui.common.MenuItem
import com.seblful.klas.ui.common.MenuOptions
import com.seblful.klas.ui.common.ModeField
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics

/*
 * The booking form's three sections, in the order the tutor answers them: who the lesson is for, when it
 * is, and on what terms. See [ClassEditScreen] for the screen around them.
 */

/** One titled card of the form. */
@Composable
internal fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard {
        Column(
            Modifier.padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            SectionTitle(title)
            content()
        }
    }
}

/**
 * Who: the person or group, the group's roster, and the subject. A new booking picks from one list of
 * students and groups together, so there is no «Student or group?» to answer first. An existing lesson keeps its kind: turning a student's class into a group lesson (or back)
 * would silently create or destroy other students' billed classes.
 */
@Composable
internal fun WhoSection(
    state: ClassFormState,
    isNew: Boolean,
    viewModel: ClassEditViewModel,
    setup: BookingSetupNavigation,
) {
    val form = state.form
    if (isNew) {
        val choices by viewModel.targetChoices.collectAsStateWithLifecycle()
        WhoField(choices, chosen = form.assignee, state = state, onChoose = { viewModel.choose(it) }, setup)
    } else {
        when (form.target) {
            ClassTarget.STUDENT -> DropdownField(
                label = stringResource(R.string.class_student_label),
                selectedText = state.studentName,
                options = remember(state.students) { state.students.map { it.id to it.name } },
                onSelect = { id -> viewModel.change(BookingIntent.ChooseStudent(id)) },
                selectedId = form.studentId,
            )
            // A booked lesson can't be handed to another group: its members' classes are billed history,
            // and the other group is a different set of people.
            ClassTarget.GROUP -> DropdownField(
                label = stringResource(R.string.class_group_label),
                selectedText = state.groupName,
                options = emptyList(),
                onSelect = {},
                enabled = false,
            )
        }
    }
    // Right under the group it belongs to, rather than below the time and the repeat.
    if (form.target == ClassTarget.GROUP) GroupRoster(state, isNew, viewModel, setup)
    SubjectField(state, viewModel, setup)
}

/**
 * Who, picked from a list like the subject: everyone who can be booked — the most recently taught first,
 * students and groups together — then the way to add somebody who is not there yet.
 */
@Composable
private fun WhoField(
    choices: List<TargetChoice>,
    chosen: Assignee?,
    state: ClassFormState,
    onChoose: (Assignee) -> Unit,
    setup: BookingSetupNavigation,
) {
    MenuField(
        label = stringResource(R.string.class_who_label),
        selectedText = if (state.form.target == ClassTarget.GROUP) state.groupName else state.studentName,
        enabled = true,
    ) { dismiss ->
        MenuOptions(choices, nameOf = { it.name }) { choice ->
            TickedMenuItem(
                text = choice.name,
                selected = choice.who == chosen,
                onClick = { onChoose(choice.who); dismiss() },
                leadingIcon = {
                    Icon(
                        if (choice.who is Assignee.Group) KlasIcons.Outlined.Group else KlasIcons.Outlined.Person,
                        contentDescription = null,
                    )
                },
            )
        }
        if (choices.isNotEmpty()) KlasDivider()
        MenuItem(
            text = { Text(stringResource(R.string.schedule_add_student)) },
            onClick = { dismiss(); setup.addStudent() },
            leadingIcon = { Icon(KlasIcons.Outlined.PersonAdd, contentDescription = null) },
        )
        MenuItem(
            text = { Text(stringResource(R.string.group_add_title)) },
            onClick = { dismiss(); setup.addGroup() },
            leadingIcon = { Icon(KlasIcons.Outlined.Groups, contentDescription = null) },
        )
    }
}

/** These ticks correct the booked roster. Absences are recorded separately, in the lesson sheet. */
@Composable
private fun GroupRoster(
    state: ClassFormState,
    isNew: Boolean,
    viewModel: ClassEditViewModel,
    setup: BookingSetupNavigation,
) {
    val form = state.form
    if (state.groupHasNoMembers) {
        Text(
            stringResource(R.string.class_group_no_active_members),
            style = MaterialTheme.typography.bodySmall,
            color = klasSemantics.validation,
        )
        form.groupId?.let { id ->
            AddRow(stringResource(R.string.group_edit_members), onClick = { setup.groupEdit(id) })
        }
    }
    if (state.members.isEmpty()) return
    Text(stringResource(R.string.class_group_members_title), style = MaterialTheme.typography.labelLarge)
    // Distinguish correcting the booked roster from recording an absence.
    if (!isNew) {
        Text(
            stringResource(R.string.class_group_members_editable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // The same roster picker as the group form, applied to this lesson's membership.
    state.members.forEach { student ->
        MemberPickRow(
            student = student,
            selected = student.id !in form.excludedMemberIds,
            onToggle = { on -> viewModel.change(BookingIntent.IncludeMember(student.id, on)) },
        )
    }
    if (state.allMembersExcluded) {
        Text(
            stringResource(R.string.class_group_no_members_left),
            style = MaterialTheme.typography.bodySmall,
            color = klasSemantics.validation,
        )
    }
}

@Composable
private fun SubjectField(state: ClassFormState, viewModel: ClassEditViewModel, setup: BookingSetupNavigation) {
    DropdownField(
        label = stringResource(R.string.class_subject_label),
        selectedText = state.subjectName,
        options = remember(state.subjects) { state.subjects.map { it.id to it.name } },
        onSelect = { id -> viewModel.change(BookingIntent.ChooseSubject(id)) },
        selectedId = state.form.subjectId,
        enabled = state.subjectPickerEnabled,
        supportingText = state.subjectNotice?.let { notice ->
            stringResource(
                when (notice) {
                    SubjectNotice.FIXED_BY_GROUP -> R.string.class_subject_from_group
                    SubjectNotice.NONE_FOR_GROUP -> R.string.class_no_subjects_for_group
                    SubjectNotice.NONE_FOR_STUDENT -> R.string.class_no_subjects_for_student
                },
            )
        },
        // «Предмет задан группой» is a fact about how this form works; the other two are the reason nothing
        // can be booked. Only the second kind is red.
        isError = state.subjectNotice != null && state.subjectNotice != SubjectNotice.FIXED_BY_GROUP,
    )
    when (state.subjectNotice) {
        SubjectNotice.NONE_FOR_STUDENT -> state.form.studentId?.let { id ->
            AddRow(stringResource(R.string.subject_set_price), onClick = { setup.studentSubjects(id) })
        }
        SubjectNotice.NONE_FOR_GROUP -> AddRow(stringResource(R.string.subject_add), setup.subjects)
        else -> Unit
    }
}
