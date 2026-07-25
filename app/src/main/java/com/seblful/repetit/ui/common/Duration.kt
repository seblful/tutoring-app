package com.seblful.repetit.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.seblful.repetit.R
import com.seblful.repetit.format.hoursLabelOrNull

/**
 * Short label for a duration in minutes — "30 мин", "1 ч", "1,5 ч". The single source of truth, so
 * the option chips and any prose quoting the chosen value (the reminder subtitle) can't drift apart.
 */
@Composable
@ReadOnlyComposable
fun durationLabel(minutes: Int): String {
    val hours = hoursLabelOrNull(minutes)
    return if (hours != null) stringResource(R.string.duration_hours, hours)
    else stringResource(R.string.duration_minutes, minutes)
}
