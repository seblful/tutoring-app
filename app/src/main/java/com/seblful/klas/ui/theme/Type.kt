package com.seblful.klas.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.seblful.klas.R

/**
 * The one family the whole app sets its type in: **Golos Text**.
 *
 * A single constant rather than the family repeated per style, because the styles below must not be
 * able to disagree: the scale used to name the family style by style and set the weight only on some
 * of them, which left `titleSmall` — every report card's title, every day header — rendering at the
 * platform's regular weight beside a `titleMedium` that had been made semi-bold. The identity was
 * declared but applied unevenly, which reads as a mistake rather than as a choice.
 *
 * ### Why a face at all, and why this one
 *
 * The app was set in `FontFamily.Default`, which on Android is Roboto and renders Cyrillic perfectly
 * well — so this is not a correctness fix. It is the cheapest thing that stops an app looking like the
 * template it was started from, because the face is the one design decision visible in every single
 * pixel of text on every screen.
 *
 * Golos Text is drawn **Cyrillic-first** (Paratype), which is the right way round for an app whose UI
 * is pinned to Russian: the Latin here is the borrowed alphabet, not the other way about. It is a text
 * face rather than a display one — made to stay legible at a list row's 14sp, which is the size most of
 * this app is — and it is [OFL][R.raw.golos_text_ofl] licensed, so it can simply ship.
 *
 * ### Four static instances, not one variable font
 *
 * Upstream ships a single variable `GolosText[wght].ttf`. Android honours a variable axis only from
 * **API 26**, and this app's `minSdk` is 24 — on 24 and 25 a variable font loads at its default
 * instance, so every weight below would have come out Regular and the whole scale would have
 * flattened on exactly the old devices least able to spare the contrast. So the four weights the scale
 * actually asks for are instanced out of the axis ahead of time (`fvar` dropped, `usWeightClass` set)
 * and shipped as four ordinary statics.
 *
 * Deliberately **not subsetted**. The face is a compact 566 codepoints to begin with, so trimming it
 * would save little, and the thing it would cost is real: a student's name is free text, and Belarusian
 * «і», «ў» or Ukrainian «ї», «є» in a Minsk tutor's roster would come back as tofu. Keeping the face
 * whole makes that impossible.
 */
private val AppFontFamily = FontFamily(
    Font(R.font.golos_text_regular, FontWeight.Normal),
    Font(R.font.golos_text_medium, FontWeight.Medium),
    Font(R.font.golos_text_semibold, FontWeight.SemiBold),
    Font(R.font.golos_text_bold, FontWeight.Bold),
)

/**
 * The type scale: **every** style stated, none left to the M3 baseline.
 *
 * Headlines and titles are a touch bolder and tighter than Material's defaults, for a more confident
 * feel; body and label styles stay at comfortable reading weights. Partially overriding a scale is
 * what produces the mismatch above, so the full set is written out even where a style matches the
 * default it replaces.
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.3).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    // Semi-bold like the two titles above it. This is the style the section headings are drawn in —
    // the report cards, the settings groups, the schedule's day headers — so leaving it at the
    // platform's regular weight was the single most visible place the scale contradicted itself.
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.4.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
