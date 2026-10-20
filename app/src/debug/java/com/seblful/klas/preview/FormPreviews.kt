package com.seblful.klas.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.FilterChipRow
import com.seblful.klas.ui.common.MoneyTextField

/*
 * The controls a tutor answers with — a money field, a choice, a narrowing filter — and the dialog
 * that asks before something is lost. Same rules as `CommonPreviews.kt`: Russian text, plausible
 * numbers, every preview a screenshot test.
 */

/**
 * A money field in the three states a form shows it in: blank with its hint, typed and valid, and
 * typed wrong.
 *
 * The rule under test is the supporting line's — *the error wins over the hint* — and the red that
 * comes only with something unusable actually typed: a blank field is incomplete, not wrong, so it must
 * not be red. The third field is a payment of zero, the one error that is about meaning, not spelling.
 */
@KlasPreview
@Composable
private fun MoneyTextFieldPreview() = PreviewSurface {
    MoneyTextField(
        field = MoneyField(),
        onValueChange = {},
        label = "Цена за час",
        hint = "Можно оставить пустым",
    )
    MoneyTextField(field = MoneyField("35,50"), onValueChange = {}, label = "Цена за час")
    MoneyTextField(
        field = MoneyField("0", kind = MoneyField.Kind.PAYMENT),
        onValueChange = {},
        label = "Сумма платежа",
    )
}

/**
 * A settings choice on both sides of its bound: three options as segments, five as a field.
 *
 * [ChoiceRow] switches shape past four options because a fifth segment leaves no width to shrink a
 * label into; the pair is what shows the two shapes are one row — same title, same description slot —
 * and the 1.5× render is where the segments' one-line labels have to shrink rather than wrap.
 */
@KlasPreview
@Composable
private fun ChoiceRowPreview() = PreviewSurface {
    ChoiceRow(
        title = "Тема",
        options = listOf("SYSTEM" to "Как в системе", "LIGHT" to "Светлая", "DARK" to "Тёмная"),
        selected = "SYSTEM",
        onSelect = {},
    )
    ChoiceRow(
        title = "Длительность по умолчанию",
        description = "Подставляется при записи нового занятия",
        options = listOf(45 to "45 мин", 60 to "1 ч", 90 to "1,5 ч", 120 to "2 ч", 180 to "3 ч"),
        selected = 90,
        onSelect = {},
    )
}

/**
 * The chip that says a list is narrowed, with a name long enough to be cut.
 *
 * It sits outside the scroll so a narrowed list is never seen without it; the name ellipsizes rather
 * than pushing the close mark off the chip, which is the part that must survive 1.5×.
 */
@KlasPreview
@Composable
private fun FilterChipRowPreview() = PreviewSurface {
    FilterChipRow(name = "Анастасия Владимировна Константинопольская", onClear = {})
}

/**
 * A confirmation that loses something: «Удалить» in the error colour.
 *
 * The rule is that the last tap before a loss looks the same everywhere, and the red is what says it —
 * in dark mode the error tone is a pale one, which is the render worth a look.
 */
@KlasPreview
@Composable
private fun ConfirmDialogPreview() = PreviewSurface {
    ConfirmDialog(
        // The app's own longest delete question, so the preview wraps where the real dialog does.
        text = stringResource(R.string.class_delete_completed_confirm),
        destructive = true,
        onConfirm = {},
        onDismiss = {},
    )
}
