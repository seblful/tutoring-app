package com.seblful.klas.ui.schedule.booking

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.FormSaveBar
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.schedule.timeConflictText
import com.seblful.klas.ui.theme.KlasMotion
import com.seblful.klas.ui.theme.klasSemantics

/**
 * The booking form's [FormSaveBar]: what it will book beside the save — or, while it cannot, the one thing in
 * the way, which here can be a reason the button alone would not say (a taken hour).
 */
@Composable
internal fun BookingSaveBar(
    state: ClassFormState,
    isNew: Boolean,
    bottomInset: PaddingValues,
    onSave: () -> Unit,
) {
    FormSaveBar(
        label = stringResource(if (isNew) R.string.class_book_action else R.string.action_save),
        enabled = state.canSave,
        onSave = onSave,
        bottomInset = bottomInset,
    ) { SaveBarText(state) }
}

@Composable
private fun SaveBarText(state: ClassFormState) {
    val error = klasSemantics.validation
    when (val bar = state.saveBar) {
        is SaveBar.Missing -> TwoLines(
            stringResource(R.string.class_still_needed),
            bar.gaps.map { gapLabel(it) }.joinToString(),
        )
        SaveBar.Taken -> TwoLines(
            state.conflict?.let { timeConflictText(it) } ?: stringResource(R.string.class_series_refused_all_taken),
            color = error,
        )
        SaveBar.NoBookableMembers -> TwoLines(stringResource(R.string.class_group_no_active_members), color = error)
        SaveBar.NoneTicked -> TwoLines(stringResource(R.string.class_group_no_members_left), color = error)
        SaveBar.Ready -> TwoLines(whenLine(state), whoLine(state))
    }
}

@Composable
private fun TwoLines(headline: String, supporting: String? = null, color: Color = Color.Unspecified) {
    Text(headline, style = MaterialTheme.typography.titleSmall, color = color)
    if (supporting != null) {
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** «Thu, 22 Oct · 16:30 · until 17:30», with the count when it repeats. */
@Composable
private fun whenLine(state: ClassFormState): String {
    val form = state.form
    return bulletJoin(
        form.date?.formatDate().orEmpty(),
        form.timeFrom?.timeText().orEmpty(),
        state.ending?.let { endsAtText(it) }.orEmpty(),
        if (form.repeat.repeats) {
            pluralStringResource(R.plurals.class_series_lessons, form.repeatCount, form.repeatCount)
        } else {
            ""
        },
    )
}

/** «Anna Ivanova · Maths · 40 BYN/h». */
@Composable
private fun whoLine(state: ClassFormState): String = bulletJoin(
    if (state.form.target == ClassTarget.GROUP) state.groupName else state.studentName,
    state.subjectName,
    state.form.price.amount?.let { stringResource(R.string.price_per_hour, it.text()) }.orEmpty(),
)

@Composable
private fun gapLabel(gap: BookingGap): String = stringResource(
    when (gap) {
        BookingGap.WHO -> R.string.class_gap_who
        BookingGap.SUBJECT -> R.string.class_gap_subject
        BookingGap.DATE -> R.string.class_gap_date
        BookingGap.START -> R.string.class_gap_start
        BookingGap.LENGTH -> R.string.class_gap_length
        BookingGap.PRICE -> R.string.class_gap_price
    },
)
