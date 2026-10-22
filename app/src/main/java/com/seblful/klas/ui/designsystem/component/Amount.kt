package com.seblful.klas.ui.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.seblful.klas.ui.theme.klasSemantics

/** What an amount means, which decides its colour: a debt is said in the debt role, the rest in ink. */
enum class AmountMeaning { Plain, Debt, Credit }

/**
 * Money as the design system sees it: already-worded text ("Owes 200 $", "80 $ in credit") and what it means.
 * The words come with it, because the debt colour is shared with other meanings and may not be the only
 * signal. `ui/shared` turns a `Money` and a balance into one; the design system never sees `Money`.
 */
@Immutable
data class AmountText(val text: String, val meaning: AmountMeaning = AmountMeaning.Plain)

/** An amount at the trailing edge: tabular digits, so a column of amounts lines up. */
@Composable
fun Amount(amount: AmountText, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(
        amount.text,
        style = remember(style) { style.copy(fontFeatureSettings = TabularFigures) },
        color = when (amount.meaning) {
            AmountMeaning.Debt -> klasSemantics.debt
            AmountMeaning.Credit, AmountMeaning.Plain -> Color.Unspecified
        },
        maxLines = 1,
        modifier = modifier,
    )
}

private const val TabularFigures = "tnum"
