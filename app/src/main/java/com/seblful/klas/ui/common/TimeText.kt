package com.seblful.klas.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import com.seblful.klas.format.formatDateTime
import com.seblful.klas.format.formatHour
import com.seblful.klas.format.formatTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

@Composable
@ReadOnlyComposable
private fun hour12(): Boolean = !DateFormat.is24HourFormat(LocalContext.current.applicationContext)

/** e.g. "21:00", or "9:00 PM" on a phone set to a 12-hour clock. */
@Composable
@ReadOnlyComposable
fun Instant.timeText(): String = formatTime(hour12())

/** A whole hour as a scale marks it: "9:00", or "9 AM" on a 12-hour phone. */
@Composable
@ReadOnlyComposable
fun Instant.hourText(): String = formatHour(hour12())

/** [timeText] for a caller already holding a wall-clock time — a booking form's from/to. */
@Composable
@ReadOnlyComposable
fun LocalTime.timeText(): String = formatTime(hour12())

/** e.g. "29 июл., 21:00", or "29 июл., 9:00 PM" on a 12-hour phone. */
@Composable
@ReadOnlyComposable
fun Instant.dateTimeText(): String = formatDateTime(hour12())

/**
 * A time leading a row in a **column of times**: tabular digits, at the width of the widest time this phone
 * writes, so «9:00» and «11:00» take one width and whatever follows them starts on one edge. Golos's
 * default digits are proportional — a «1» is a fifth narrower than a «0».
 */
@Composable
fun ColumnTime(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
) {
    val tabular = style.copy(fontFeatureSettings = TabularFigures)
    Text(
        text,
        style = tabular,
        color = color,
        textDecoration = textDecoration,
        maxLines = 1,
        modifier = modifier.width(widestTimeWidth(tabular)),
    )
}

/** Two-digit hours, morning and evening: with tabular digits, the widest a time can be on either clock. */
@Composable
private fun widestTimeWidth(style: TextStyle): Dp {
    val samples = listOf(LocalTime(10, 0).timeText(), LocalTime(22, 0).timeText())
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(samples, style, density) {
        with(density) { samples.maxOf { measurer.measure(it, style).size.width }.toDp() }
    }
}

private const val TabularFigures = "tnum"
