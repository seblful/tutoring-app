package com.seblful.klas.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R

/** What a student or group form still lacks before it can save — said on its save bar, each by name. */
enum class FormGap { NAME, SUBJECT, PRICE, MEMBERS }

/** «Still needed / a name, a price» on a [com.seblful.klas.ui.designsystem.component.FormSaveBar]; nothing once ready. */
@Composable
fun StillNeeded(gaps: List<FormGap>) {
    if (gaps.isEmpty()) return
    Text(stringResource(R.string.class_still_needed), style = MaterialTheme.typography.titleSmall)
    Text(
        gaps.map { stringResource(it.label) }.joinToString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val FormGap.label: Int
    get() = when (this) {
        FormGap.NAME -> R.string.form_gap_name
        FormGap.SUBJECT -> R.string.form_gap_subject
        FormGap.PRICE -> R.string.form_gap_price
        FormGap.MEMBERS -> R.string.form_gap_members
    }
