package com.seblful.klas.ui.schedule.booking

import androidx.compose.runtime.Composable
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.FormBar
import com.seblful.klas.ui.schedule.timeConflictText

/**
 * Booking's [SaveBar] as the form bar every form shows — what it will book, or, while it cannot, the one thing
 * in the way, which here can be a reason the button alone would not say (a taken hour). Ready exactly when
 * `canSave` (ClassFormState).
 */
@Composable
fun bookingBar(state: ClassFormState): FormBar = when (val bar = state.saveBar) {
    is SaveBar.Missing -> FormBar.Missing(bar.gaps.map { gapLabel(it) })
    SaveBar.Taken -> FormBar.Blocked(
        state.conflict?.let { timeConflictText(it) } ?: stringResource(Strings.class_series_refused_all_taken),
    )
    SaveBar.NoBookableMembers -> FormBar.Blocked(stringResource(Strings.class_group_no_active_members))
    SaveBar.NoneTicked -> FormBar.Blocked(stringResource(Strings.class_group_no_members_left))
    SaveBar.Ready -> FormBar.Ready(whenLine(state), whoLine(state))
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
            pluralStringResource(Plurals.class_series_lessons, form.repeatCount, form.repeatCount)
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
    state.form.price.amount?.let { stringResource(Strings.price_per_hour, it.text()) }.orEmpty(),
)

@Composable
private fun gapLabel(gap: BookingGap): String = stringResource(
    when (gap) {
        BookingGap.WHO -> Strings.class_gap_who
        BookingGap.SUBJECT -> Strings.class_gap_subject
        BookingGap.DATE -> Strings.class_gap_date
        BookingGap.START -> Strings.class_gap_start
        BookingGap.LENGTH -> Strings.class_gap_length
        BookingGap.PRICE -> Strings.class_gap_price
    },
)
