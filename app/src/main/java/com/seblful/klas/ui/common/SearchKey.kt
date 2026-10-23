package com.seblful.klas.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection

/**
 * What `/` on a hardware keyboard opens: the search of the screen in front. A screen with a search claims
 * it while shown ([KlasScreen]); the newest claim wins, as the newest screen is the one on top.
 */
class SearchKey {
    private val openers = mutableListOf<() -> Unit>()

    /** Opens the front screen's search — false when no screen on show has one. */
    fun press(): Boolean {
        val open = openers.lastOrNull() ?: return false
        open()
        return true
    }

    internal fun claim(open: () -> Unit) {
        openers += open
    }

    internal fun release(open: () -> Unit) {
        openers -= open
    }
}

/** The activity's [SearchKey]; null under previews and tests that drive no keyboard. */
val LocalSearchKey = staticCompositionLocalOf<SearchKey?> { null }

/**
 * A mouse's right button does what a long-press does (R7: it picks) — what a Chromebook or desktop user
 * reaches for. Taken before the click handler under it sees the press, so it is not also a tap.
 */
fun Modifier.rightClickAsLongPress(onLongClick: (() -> Unit)?): Modifier =
    if (onLongClick == null) {
        this
    } else {
        pointerInput(onLongClick) {
            awaitEachGesture {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    onLongClick()
                }
            }
        }
    }

/**
 * ← and → page the dates, for the focused date title only — never on rows, where arrows move focus. Mirrored
 * right to left. Any other key passes on.
 */
fun pageByArrow(event: KeyEvent, direction: LayoutDirection, onPrevious: () -> Unit, onNext: () -> Unit): Boolean {
    val step = when (event.key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> 0
    }
    if (event.type != KeyEventType.KeyDown || step == 0) return false
    val ahead = if (direction == LayoutDirection.Ltr) step > 0 else step < 0
    if (ahead) onNext() else onPrevious()
    return true
}
