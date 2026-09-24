package com.seblful.klas.ui.navigation

/**
 * One tap in the tray that a tab has to act on **once** — put the schedule on a day, open a lesson's sheet,
 * scroll the reports to the debts.
 *
 * A tab is the graph's to keep: its screen leaves composition whenever a pushed screen or another tab
 * covers it, and is handed the same arrival again when it comes back, so it has to be able to tell "the
 * tap I already acted on" from "a new one". Its view model outlives those returns and does that, through
 * an [ArrivalGate].
 *
 * **Compared by identity, which is why this is a class and not a data class.** Two taps on one notification
 * are two instances and so two asks, though they say the same thing. It replaced a counter held in
 * `AppRoot`'s plain `remember`, which a change of language or theme — both still rebuild the activity —
 * put back to zero while the view model kept the number it had last honoured: the next tap was then
 * numbered as one it had already seen, and was ignored.
 */
class Arrival<out T>(val target: T)

/** Lets each [Arrival] through once. Held by the view model, which outlives the screen's returns. */
class ArrivalGate {

    private var last: Arrival<*>? = null

    /** [arrival]'s target the first time it is offered, and null every time after — or for no arrival. */
    fun <T> admit(arrival: Arrival<T>?): T? {
        if (arrival == null || arrival === last) return null
        last = arrival
        return arrival.target
    }
}
