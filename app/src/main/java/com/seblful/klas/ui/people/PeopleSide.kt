package com.seblful.klas.ui.people

import kotlinx.coroutines.flow.StateFlow

/**
 * One side of «Люди» — what the shared app bar works with, whichever side is up.
 *
 * The students and groups view models are deliberately two: each side keeps its own search and its
 * own archive toggle, which is what lets switching tabs preserve them. But the bar above them is
 * one — a search box, its close-clears-it rule, an archive toggle — and the screen used to dispatch
 * every one of its actions by hand: four `when (tab)` switches inside the composable, in the one
 * file of the five behind «Люди» that no test could reach. This is the seam those switches were
 * standing in for. The two view models are its adapters, and the screen picks a side **once**.
 *
 * What stays outside, on purpose: the rows and their lists (two row types, two layouts), the FAB's
 * destination, the sort menu (a students question — a group carries no balance), and the words.
 */
interface PeopleSide {

    /** What the tutor is searching for on this side; empty means the list is unfiltered. */
    val query: StateFlow<String>

    /** Whether this side lists its archived entries too. */
    val showArchived: StateFlow<Boolean>

    fun setQuery(query: String)

    fun toggleArchived()
}
