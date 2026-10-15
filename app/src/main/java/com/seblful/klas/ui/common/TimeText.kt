package com.seblful.klas.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.seblful.klas.format.formatDateTime
import com.seblful.klas.format.formatTime
import java.time.Instant
import java.time.LocalTime

/**
 * A time as the **UI** prints it: 12- or 24-hour by the phone's own clock setting, so a lesson reads the
 * way every other clock on the phone does — and the way the picker that set it does (`TimePickerModal`).
 *
 * The same shape as [Money.text] one file over, and for the same reason: the *format* belongs to
 * `format/Formatters`, but *which* of its faces to use is a question only the surroundings can answer, so
 * it is asked in composition. `format/` stays context-free, which is what lets a notification, a report
 * and a view model keep using it.
 *
 * The line is **read on the phone or away from it**, not screen or not-a-screen: a reminder is drawn by
 * `notifications/AndroidNotifier` rather than by a composable, and it asks the same question this does.
 * What keeps `HH:mm` is a report, an export, a backup — read long after the phone that produced it, where
 * a marker means nothing and `HH:mm` sorts. `ClockFormatTest` keeps a screen from reaching past these
 * three to the raw formatter, and keeps the reminder asking.
 *
 * Asked of the **application** context, never the composition's. With the phone's 12/24-hour switch on
 * "automatic", Android answers from the context's locale — and the screens' context is localized to the
 * *app's* language (`ProvideLocalizedResources`), while a bottom sheet is a window of its own holding the
 * phone's. A Russian app on an English phone printed a lesson at 16:30 in the schedule and 4:30 PM in its
 * sheet. The application context is the phone's in every window, as `AndroidNotifier`'s is (`TimeTextTest`).
 */
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
