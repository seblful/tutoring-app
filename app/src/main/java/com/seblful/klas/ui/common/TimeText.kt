package com.seblful.klas.ui.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.seblful.klas.format.formatDateTime
import com.seblful.klas.format.formatTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * Whether the phone writes times on a 12-hour clock, read once for everything under it rather than by every
 * time printed — a list row asked the system per time it held. Read again each time the app comes back to the
 * front: the setting is changed in the phone's settings, away from the app.
 */
@Composable
fun ProvideClockFormat(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    var hour12 by remember(context) { mutableStateOf(phoneHour12(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hour12 = phoneHour12(context) }
    CompositionLocalProvider(LocalHour12 provides hour12, content = content)
}

/** [ProvideClockFormat]'s answer; null outside it — a preview, a test of one row — which asks the phone. */
private val LocalHour12 = staticCompositionLocalOf<Boolean?> { null }

private fun phoneHour12(context: Context): Boolean = !DateFormat.is24HourFormat(context)

@Composable
@ReadOnlyComposable
private fun hour12(): Boolean = LocalHour12.current ?: phoneHour12(LocalContext.current.applicationContext)

/** e.g. "21:00", or "9:00 PM" on a phone set to a 12-hour clock. */
@Composable
@ReadOnlyComposable
fun Instant.timeText(): String = formatTime(hour12())

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
 *
 * [width] is [rememberColumnTimeWidth] of the same [style], measured once above the column: measured in
 * each row, every card scrolled into a list laid out two sample times of its own.
 */
@Composable
fun ColumnTime(
    text: String,
    style: TextStyle,
    width: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
) {
    Text(
        text,
        style = remember(style) { style.tabular() },
        color = color,
        textDecoration = textDecoration,
        maxLines = 1,
        modifier = modifier.width(width),
    )
}

/** Two-digit hours, morning and evening: with tabular digits, the widest a time can be on either clock. */
@Composable
fun rememberColumnTimeWidth(style: TextStyle): Dp {
    val samples = listOf(LocalTime(10, 0).timeText(), LocalTime(22, 0).timeText())
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(samples, style, density) {
        val tabular = style.tabular()
        with(density) { samples.maxOf { measurer.measure(it, tabular).size.width }.toDp() }
    }
}

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TabularFigures)

private const val TabularFigures = "tnum"
