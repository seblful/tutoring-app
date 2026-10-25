package com.seblful.klas.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.seblful.klas.R
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.ui.common.ArchiveMenuItem
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.MenuItem
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.OverflowMenu
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.theme.KlasIcons

/**
 * Under the name, as two lines: how to reach them, then how they are taught — with the ways to reach them
 * beside it. Then the tutor's note, if any. A tap on the phone, or on its message icon, asks Android which app
 * should write to them (see `ExternalTarget.Message`): [onMessage] gets the kept phone and its whole number.
 */
@Composable
internal fun ContactBlock(
    student: Student,
    onCall: (String) -> Unit,
    onMessage: (phone: String, international: String?) -> Unit,
    onEmail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phone = Phone.contactOf(student.phone)
    val international = Phone.international(student.phone, LocalCurrency.current)
    val email = student.email.takeIf { it.isNotBlank() }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            // The tap area is the box, so the line sits in its middle, level with the icon beside it.
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (phone == null) Modifier else {
                            Modifier
                                .heightIn(min = MinTouchTarget)
                                .clickable(role = Role.Button) { onMessage(phone, international) }
                        },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    val style = MaterialTheme.typography.bodyMedium
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    // The email stands in the line only for want of a phone; its button is there either way.
                    (phone ?: email)?.let { Text(it, style = style, color = color) }
                    Text(classModeLabel(student.defaultMode), style = style, color = color)
                }
            }
            if (phone != null) {
                ContactAction(KlasIcons.Outlined.Call, stringResource(R.string.student_call)) { onCall(phone) }
                ContactAction(KlasIcons.Outlined.Send, stringResource(R.string.student_message)) {
                    onMessage(phone, international)
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
    onMessage: (phone: String, international: String?) -> Unit,
    onEmail: (String) -> Unit,
    onArchive: () -> Unit,
    /** A reminder of what is owed, offered while something is. */
    remind: (() -> Unit)? = null,
) {
    val phone = Phone.contactOf(student.phone)
    val international = Phone.international(student.phone, LocalCurrency.current)
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
                label = stringResource(R.string.student_message),
                icon = KlasIcons.Outlined.Send,
                onClick = { close(); onMessage(phone, international) },
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
            KlasDivider()
        }
        // The statement is the balance card's; a reminder, while something is owed, is sent from here.
        if (remind != null) {
            ContactMenuItem(
                label = stringResource(R.string.student_send_reminder),
                icon = KlasIcons.Outlined.Payments,
                onClick = { close(); remind() },
            )
            KlasDivider()
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
    MenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
