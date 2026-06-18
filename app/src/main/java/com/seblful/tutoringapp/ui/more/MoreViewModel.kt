package com.seblful.tutoringapp.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.ClassRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val classes: ClassRepository,
) : ViewModel() {

    /** Backfills calendar events for existing classes once the user grants calendar access. */
    fun onCalendarEnabled() {
        viewModelScope.launch { classes.resyncCalendar() }
    }
}
