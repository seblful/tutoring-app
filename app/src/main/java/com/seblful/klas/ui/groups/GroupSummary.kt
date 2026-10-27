package com.seblful.klas.ui.groups

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.text

/** «Math · 4 members · 12 $/h per student»: what the save bar sums up once the group is ready. */
@Composable
internal fun groupSummary(state: GroupFormViewModel.UiState): String {
    val members = state.selectedMemberIds.size
    return bulletJoin(
        state.subjects.firstOrNull { it.id == state.form.subjectId }?.name.orEmpty(),
        pluralStringResource(R.plurals.members_count, members, members),
        state.price?.let { stringResource(R.string.price_per_hour_per_student, it.text()) }.orEmpty(),
    )
}
