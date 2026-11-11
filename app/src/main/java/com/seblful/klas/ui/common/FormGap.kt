package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.designsystem.component.FormBar

/** What a student or group form still lacks before it can save — said on its save bar, each by name. */
enum class FormGap { NAME, SUBJECT, PRICE, MEMBERS }

/** The bar a student or group form shows: its [missing] gaps by name, else [ready] — what it will save. */
@Composable
fun formBar(missing: List<FormGap>, ready: @Composable () -> FormBar.Ready): FormBar =
    if (missing.isEmpty()) ready() else FormBar.Missing(missing.map { stringResource(it.label) })

private val FormGap.label: StringResource
    get() = when (this) {
        FormGap.NAME -> Strings.form_gap_name
        FormGap.SUBJECT -> Strings.form_gap_subject
        FormGap.PRICE -> Strings.form_gap_price
        FormGap.MEMBERS -> Strings.form_gap_members
    }
