package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.DaySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// Real text metrics: the legacy stand-ins measure a line of text taller than its style's line height.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaySummaryLineTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an empty day keeps the line's height, so the pinned header does not jump`() {
        var summary by mutableStateOf(DaySummary(lessons = 2, minutes = 120, money = Money.ZERO, cancelled = 0))
        // A phone's width: the test window's 160 dp wraps the summary.
        compose.setContent { Column(Modifier.requiredWidth(360.dp).testTag(TAG)) { DaySummaryLine(summary) } }
        val busy = compose.onNodeWithTag(TAG).fetchSemanticsNode().size.height
        assertTrue(busy > 0)

        summary = DaySummary.NONE
        compose.waitForIdle()

        assertEquals(busy, compose.onNodeWithTag(TAG).fetchSemanticsNode().size.height)
    }

    private companion object {
        const val TAG = "header"
    }
}
