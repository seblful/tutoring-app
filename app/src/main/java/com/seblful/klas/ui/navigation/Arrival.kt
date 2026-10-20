package com.seblful.klas.ui.navigation

/**
 * One tap in the tray that a tab has to act on **once** — put the schedule on a day, open a lesson's sheet,
 * scroll the reports to the debts.
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

    /** hether [arrival] is one this gate still owes — **offering it does not spend it**. */
    fun <T> owes(arrival: Arrival<T>?): Boolean = arrival != null && arrival !== last

    /** Records [arrival] as honoured, so [owes] answers false for it from here on. No arrival, nothing. */
    fun honour(arrival: Arrival<*>?) {
        if (arrival != null) last = arrival
    }
}
