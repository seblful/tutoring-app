package com.seblful.klas.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.ui.common.ArchiveMenuItem
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.OverflowMenu
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.theme.KlasIcons

@Composable
internal fun ContactBlock(
    student: Student,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit,
    onTelegram: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        if (student.note.isNotBlank()) {
            Text(
                student.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.tight),
            )
        }
        Phone.contactOf(student.phone)?.let { phone ->
            ContactRow(
                icon = KlasIcons.Outlined.Call,
                text = phone,
                actionLabel = stringResource(R.string.student_call),
                onClick = { onCall(phone) },
                trailing = {
                    IconButton(onClick = { onTelegram(phone) }) {
                        Icon(
                            KlasIcons.Filled.Send,
                            stringResource(R.string.student_write_telegram),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
        if (student.email.isNotBlank()) {
            ContactRow(
                icon = KlasIcons.Outlined.Mail,
                text = student.email,
                actionLabel = stringResource(R.string.student_write_email),
                onClick = { onEmail(student.email) },
            )
        }
        ModeRow(student.defaultMode)
    }
}

@Composable
private fun ModeRow(mode: ClassMode) {
    val label = classModeLabel(mode)
    val spoken = "${stringResource(R.string.student_mode_label)}: $label"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (mode) {
                ClassMode.IN_PERSON -> KlasIcons.Outlined.LocationOn
                ClassMode.REMOTE -> KlasIcons.Outlined.Videocam
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ContactGlyph),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.leading),
        )
    }
}

private val ContactGlyph = 18.dp

@Composable
private fun ContactRow(
    icon: ImageVector,
    text: String,
    /** What a tap does, said by a screen reader as «дважды нажмите, чтобы позвонить» — a number alone is not. */
    actionLabel: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .outsetRowPress { Modifier.clickable(it, indication = null, onClickLabel = actionLabel, onClick = onClick) }
            .heightIn(min = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ContactGlyph),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.leading),
        )
        trailing?.invoke()
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
        // **Reaching the student, from any tab.** The contact block is the top of the card, which is the
        // right place for it — it is identity, not content — but it is also the part that scrolls away
        // first, and a tutor two hundred rows into «Занятия» who wants to ring about the next one had to
        // scroll all the way back for a number. The bar does not: it is the one thing on this screen that
        // stays.
        //
        // Not a duplicate control so much as the same act reached the way an *action* is reached. The block
        // still shows the phone, the email and the note — those are facts about the student, and reading
        // one is why it stays visible; these are the three things you can *do* with them.
        if (phone != null) {
            ContactMenuItem(
                label = stringResource(R.string.student_call),
                icon = KlasIcons.Filled.Call,
                onClick = { close(); onCall(phone) },
            )
            ContactMenuItem(
                label = stringResource(R.string.student_write_telegram),
                icon = KlasIcons.Filled.Send,
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
