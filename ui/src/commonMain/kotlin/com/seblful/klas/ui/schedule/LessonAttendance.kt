package com.seblful.klas.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.insetRowPress

/**
 * Who came, as value rows — «Attendance · Expected» for a 1:1 lesson, a row per member for a group's. On
 * every lesson, because a 1:1 lesson can be an absence that is still billed (domain-rules). A tap opens the
 * choices beside the value, as Settings' «Before lesson» does, with no ▾ that reads as a form field; a change
 * is undone from its message.
 */
@Composable
fun LessonAttendance(row: ScheduleRow, onAttendance: (TutoringClass, Attended) -> Unit) {
    val present = if (row.session.status.isBillable) Attended.PRESENT else Attended.EXPECTED
    val options = listOf(present, Attended.ABSENT, Attended.ABSENT_BILLED)
    Column(Modifier.padding(vertical = Spacing.tight)) {
        row.classes.forEachIndexed { index, klass ->
            AttendanceRow(
                label = if (row.isGroup) row.members.getOrElse(index) { row.title } else {
                    stringResource(Strings.class_attendance_label)
                },
                selected = klass.attended,
                options = options,
                onSelect = { onAttendance(klass, it) },
            )
        }
    }
}

@Composable
private fun AttendanceRow(label: String, selected: Attended, options: List<Attended>, onSelect: (Attended) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val value = attendanceLabel(selected)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .insetRowPress { Modifier.clickable(it, indication = null, role = Role.DropdownList) { open = true } }
                .heightIn(min = MinTouchTarget)
                .padding(horizontal = SheetGutter, vertical = Spacing.tight)
                .semantics(mergeDescendants = true) { stateDescription = value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        KlasMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                TickedMenuItem(text = attendanceLabel(option), selected = option == selected, onClick = {
                    open = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
private fun attendanceLabel(attended: Attended): String = stringResource(
    when (attended) {
        Attended.EXPECTED -> Strings.class_attendance_expected
        Attended.PRESENT -> Strings.class_attendance_present
        Attended.ABSENT -> Strings.class_attendance_absent
        Attended.ABSENT_BILLED -> Strings.class_attendance_absent_billed
    },
)
