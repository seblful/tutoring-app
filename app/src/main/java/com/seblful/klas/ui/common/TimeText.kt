package com.seblful.klas.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.seblful.klas.format.formatDateTime
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

/** [timeText] for a caller already holding a wall-clock time — a booking form's from/to. */
@Composable
@ReadOnlyComposable
fun LocalTime.timeText(): String = formatTime(hour12())

/** e.g. "29 июл., 21:00", or "29 июл., 9:00 PM" on a 12-hour phone. */
@Composable
@ReadOnlyComposable
fun Instant.dateTimeText(): String = formatDateTime(hour12())
