package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.ui.designsystem.component.FormBar

/** What a student or group form still lacks before it can save — said on its save bar, each by name. */
enum class FormGap { NAME, SUBJECT, PRICE, MEMBERS }

/** The bar a student or group form shows: its [missing] gaps by name, else [ready] — what it will save. */
@Composable
fun formBar(missing: List<FormGap>, ready: @Composable () -> FormBar.Ready): FormBar =
    if (missing.isEmpty()) ready() else FormBar.Missing(missing.map { stringResource(it.label) })

private val FormGap.label: Int
    get() = when (this) {
        FormGap.NAME -> R.string.form_gap_name
        FormGap.SUBJECT -> R.string.form_gap_subject
        FormGap.PRICE -> R.string.form_gap_price
        FormGap.MEMBERS -> R.string.form_gap_members
    }
