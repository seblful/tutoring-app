package com.seblful.klas.ui.schedule

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** The week strip's selected day, as drawn: it fills and empties rather than swapping in a frame. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarSelectionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val days = (0..6).map { LocalDate(2026, 10, 19).plus(DatePeriod(days = it)) }
    private var selected by mutableStateOf(days[0])
    private var primary = Color.Unspecified

    @Test
    fun `a tapped day fills in and the last one empties, over a few frames`() {
        compose.setContent {
            MaterialTheme {
                primary = MaterialTheme.colorScheme.primary
                Surface {
                    WeekStrip(days, Loadable.Ready(emptyMap()), selected, today = days[6], onSelect = {})
                }
            }
        }
        compose.waitForIdle()
        val unselected = disc(days[3])
        assertEquals(primary.toArgb(), disc(days[0]))

        compose.mainClock.autoAdvance = false
        selected = days[3]
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)
        val filling = disc(days[3])
        assertNotEquals("not yet full", primary.toArgb(), filling)
        assertNotEquals("already filling", unselected, filling)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(primary.toArgb(), disc(days[3]))
        assertEquals(unselected, disc(days[0]))
    }

    /** A point inside the day's disc, above its number. */
    private fun disc(day: LocalDate): Int {
        val number = compose.onNodeWithText(day.day.toString(), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val above = with(compose.density) { 12.dp.toPx() }
        return compose.activity.window.decorView.drawToBitmap()
            .getPixel(number.center.x.roundToInt(), (number.center.y - above).roundToInt())
    }
}
