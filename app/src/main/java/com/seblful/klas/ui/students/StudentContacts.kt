package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.ui.common.ArchiveMenuItem
import com.seblful.klas.ui.common.FactsLine
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.OverflowMenu
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.theme.KlasIcons

/**
 * Under the name, as one line: how to reach them, then how they are taught — with the ways to reach them
 * beside it. Then the tutor's note, if any.
 */
@Composable
internal fun ContactBlock(
    student: Student,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit,
    onTelegram: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phone = Phone.contactOf(student.phone)
    val email = student.email.takeIf { it.isNotBlank() }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            // The email stands in the line only for want of a phone; its button is there either way.
            FactsLine(
                listOf(phone ?: email.orEmpty(), classModeLabel(student.defaultMode)),
                modifier = Modifier.weight(1f),
            )
            if (phone != null) {
                ContactAction(KlasIcons.Outlined.Call, stringResource(R.string.student_call)) { onCall(phone) }
                ContactAction(KlasIcons.Outlined.Send, stringResource(R.string.student_write_telegram)) {
                    onTelegram(phone)
                }
            }
            if (email != null) {
                ContactAction(KlasIcons.Outlined.Mail, stringResource(R.string.student_write_email)) { onEmail(email) }
            }
        }
        if (student.note.isNotBlank()) {
            Text(
                student.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.tight, end = Spacing.gutter - Spacing.tight),
            )
        }
    }
}

@Composable
private fun ContactAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun StudentOverflowMenu(
    student: Student,
    onCall: (String) -> Unit,
    onTelegram: (String) -> Unit,
    onEmail: (String) -> Unit,
    onArchive: () -> Unit,
) {
    val phone = Phone.contactOf(student.phone)
    OverflowMenu { close ->
        // Reaching the student from anywhere on the card: the contact line under the name is the first thing
        // to scroll away, and the bar is the one thing that stays.
        if (phone != null) {
            ContactMenuItem(
                label = stringResource(R.string.student_call),
                icon = KlasIcons.Outlined.Call,
                onClick = { close(); onCall(phone) },
            )
            ContactMenuItem(
                label = stringResource(R.string.student_write_telegram),
                icon = KlasIcons.Outlined.Send,
                onClick = { close(); onTelegram(phone) },
            )
        }
        if (student.email.isNotBlank()) {
            ContactMenuItem(
                label = stringResource(R.string.student_write_email),
                icon = KlasIcons.Outlined.Mail,
                onClick = { close(); onEmail(student.email) },
            )
        }
        if (phone != null || student.email.isNotBlank()) {
            HorizontalDivider()
        }
        ArchiveMenuItem(student.archived) { close(); onArchive() }
    }
}

@Composable
private fun ContactMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
