package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.ui.common.ChoiceField
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing

@Composable
internal fun LessonAttendance(row: ScheduleRow, onAttendance: (TutoringClass, Attended) -> Unit) {
    val present = if (row.session.status.isBillable) Attended.PRESENT else Attended.EXPECTED
    Column(
        Modifier.padding(horizontal = SheetGutter, vertical = Spacing.inline),
        verticalArrangement = Arrangement.spacedBy(Spacing.inline),
    ) {
        SectionTitle(stringResource(R.string.class_attendance_label))
        Text(
            stringResource(R.string.class_attendance_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.classes.forEachIndexed { index, klass ->
            ChoiceField(
                label = row.members.getOrElse(index) { row.title },
                selected = klass.attended,
                options = listOf(present, Attended.ABSENT, Attended.ABSENT_BILLED),
                optionLabel = { attendanceLabel(it) },
                onSelect = { onAttendance(klass, it) },
            )
        }
    }
}

@Composable
private fun attendanceLabel(attended: Attended): String = stringResource(
    when (attended) {
        Attended.EXPECTED -> R.string.class_attendance_expected
        Attended.PRESENT -> R.string.class_attendance_present
        Attended.ABSENT -> R.string.class_attendance_absent
        Attended.ABSENT_BILLED -> R.string.class_attendance_absent_billed
    },
)
