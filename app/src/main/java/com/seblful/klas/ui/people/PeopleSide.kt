package com.seblful.klas.ui.people

import kotlinx.coroutines.flow.StateFlow

/** ne side of «Люди» — what the shared app bar works with, whichever side is up. */
interface PeopleSide {

    /** What the tutor is searching for on this side; empty means the list is unfiltered. */
    val query: StateFlow<String>

    /** Whether this side lists its archived entries too. */
    val showArchived: StateFlow<Boolean>

    fun setQuery(query: String)

    fun toggleArchived()
}
