package com.seblful.klas.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ArchiveMenuItem
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.MenuItem
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.OverflowMenu
import com.seblful.klas.ui.common.PlaceLine
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.bleedVertically
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.theme.KlasIcons

/**
 * Under the name, as two lines: how to reach them, then how they are taught — with the ways to reach them
 * beside it, and the archived badge as a group's header carries it. Then the tutor's note, if any. A tap on the
 * phone, or on its message icon, asks Android which app should write to them (see `ExternalTarget.Message`):
 * [onMessage] gets the kept phone and its whole number. An email standing in for the phone writes to it.
 */
@Composable
fun ContactBlock(
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
            Column(Modifier.weight(1f)) {
                // Only the contact is pressed, in a rounded tint: the place under it is not a way to reach them.
                val reach = when {
                    phone != null -> ContactLine(phone, stringResource(Strings.student_message)) {
                        onMessage(phone, international)
                    }
                    email != null -> ContactLine(email, stringResource(Strings.student_write_email)) { onEmail(email) }
                    else -> null
                }
                reach?.let { ReachLine(it) }
                PlaceLine(student.defaultMode, student.archived)
            }
            if (phone != null) {
                ContactAction(KlasIcons.Outlined.Call, stringResource(Strings.student_call)) { onCall(phone) }
                ContactAction(KlasIcons.Outlined.Send, stringResource(Strings.student_message)) {
                    onMessage(phone, international)
                }
            }
            if (email != null) {
                ContactAction(KlasIcons.Outlined.Mail, stringResource(Strings.student_write_email)) { onEmail(email) }
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

private class ContactLine(val text: String, val label: String, val onClick: () -> Unit)

@Composable
private fun ReachLine(line: ContactLine) {
    Text(
        line.text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .bleedVertically(Spacing.tight)
            .outsetRowPress { source ->
                Modifier.clickable(
                    source,
                    indication = null,
                    onClickLabel = line.label,
                    role = Role.Button,
                    onClick = line.onClick,
                )
            }
            .padding(vertical = Spacing.tight),
    )
}

@Composable
private fun ContactAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    NamedIconButton(icon, label, onClick, tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun StudentOverflowMenu(
    student: Student,
    onCall: (String) -> Unit,
    onMessage: (phone: String, international: String?) -> Unit,
    onEmail: (String) -> Unit,
    onCopy: (label: String, text: String) -> Unit,
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
                label = stringResource(Strings.student_call),
                icon = KlasIcons.Outlined.Call,
                onClick = { close(); onCall(phone) },
            )
            ContactMenuItem(
                label = stringResource(Strings.student_message),
                icon = KlasIcons.Outlined.Send,
                onClick = { close(); onMessage(phone, international) },
            )
        }
        if (student.email.isNotBlank()) {
            ContactMenuItem(
                label = stringResource(Strings.student_write_email),
                icon = KlasIcons.Outlined.Mail,
                onClick = { close(); onEmail(student.email) },
            )
        }
        // The number as kept and the address as typed: what a contacts app or another chat is pasted into.
        if (phone != null) {
            val label = stringResource(Strings.student_copy_phone)
            ContactMenuItem(label, KlasIcons.Outlined.ContentCopy) { close(); onCopy(label, phone) }
        }
        if (student.email.isNotBlank()) {
            val label = stringResource(Strings.student_copy_email)
            ContactMenuItem(label, KlasIcons.Outlined.ContentCopy) { close(); onCopy(label, student.email) }
        }
        if (phone != null || student.email.isNotBlank()) {
            KlasDivider()
        }
        // The statement is the balance card's; a reminder, while something is owed, is sent from here.
        if (remind != null) {
            ContactMenuItem(
                label = stringResource(Strings.student_send_reminder),
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
