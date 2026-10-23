package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.designsystem.component.FormSaveBar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookingSaveBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val ready = ClassFormState(
        form = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate(2026, 6, 20),
            timeFrom = LocalTime(10, 0),
            durationMinutes = 60,
            price = MoneyField("30"),
        ),
    )
    private val taken = ready.copy(
        conflict = TimeConflict(slot = Scheduling.Slot(Instant.parse("2026-06-20T07:00:00Z"), 60), who = "Борис"),
    )

    @Test
    fun `the bar eases to its new height when what it says shortens`() {
        var state by mutableStateOf(ready)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.testTag(TAG)) {
                    FormSaveBar("Book", bookingBar(state), onSave = {}, PaddingValues(0.dp))
                }
            }
        }
        val twoLines = height()

        compose.mainClock.autoAdvance = false
        state = taken
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)
        val easing = height()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val oneLine = height()
        assertTrue("one line is shorter: $oneLine < $twoLines", oneLine < twoLines)
        assertTrue("between the two on the way: $oneLine < $easing < $twoLines", easing in (oneLine + 1) until twoLines)
    }

    private fun height() = compose.onNodeWithTag(TAG).fetchSemanticsNode().size.height

    private companion object {
        const val TAG = "bar"
    }
}
