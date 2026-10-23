package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp

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

/** The corners of a slice cut from [card]: the list's ends round, the joins between its rows stay square. */
fun SlicePosition.shapeOf(card: CornerBasedShape): CornerBasedShape {
    val square = CornerSize(0.dp)
    return card.copy(
        topStart = if (roundsTop) card.topStart else square,
        topEnd = if (roundsTop) card.topEnd else square,
        bottomStart = if (roundsBottom) card.bottomStart else square,
        bottomEnd = if (roundsBottom) card.bottomEnd else square,
    )
}
