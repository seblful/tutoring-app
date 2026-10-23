package com.seblful.klas.ui.designsystem.component

/** Where a row sits in a list drawn as one surface: the ends round its corners, the rows between are square. */
enum class SlicePosition(val roundsTop: Boolean, val roundsBottom: Boolean) {
    ONLY(roundsTop = true, roundsBottom = true),
    FIRST(roundsTop = true, roundsBottom = false),
    MIDDLE(roundsTop = false, roundsBottom = false),
    LAST(roundsTop = false, roundsBottom = true),
    ;

    companion object {
        fun of(index: Int, count: Int): SlicePosition = when {
            count == 1 -> ONLY
            index == 0 -> FIRST
            index == count - 1 -> LAST
            else -> MIDDLE
        }
    }
}
