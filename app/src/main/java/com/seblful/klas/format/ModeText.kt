package com.seblful.klas.format

import androidx.annotation.StringRes
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode

/**
 * The word a class mode is called — «Очно» / «Удалённо» — as a resource, so each reader resolves it in the
 * language it needs: an event's location in the app's chosen one, a screen in its own.
 *
 * The event's location and the screens' label are the same word on purpose — the mode is mirrored into the
 * calendar, not described there — and each spelled the mapping for itself. Here in `format/` because both
 * readers may depend on it: the calendar adapter must not depend on the UI, nor the UI on an adapter.
 */
@StringRes
fun ClassMode.labelRes(): Int = when (this) {
    ClassMode.IN_PERSON -> R.string.class_mode_in_person
    ClassMode.REMOTE -> R.string.class_mode_remote
}
