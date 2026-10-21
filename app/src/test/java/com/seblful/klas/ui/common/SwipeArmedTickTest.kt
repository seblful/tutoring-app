package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The tick a [SwipeToConfirmBox] gives as a drag crosses its mark. */
@RunWith(RobolectricTestRunner::class)
class SwipeArmedTickTest {

    @get:Rule
    val compose = createComposeRule()

    private val performed = mutableListOf<HapticFeedbackType>()

    private val ticks get() = performed.count { it == HapticFeedbackType.GestureThresholdActivate }

    private fun show() = compose.setContent {
        val recorder = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performed += hapticFeedbackType
            }
        }
        CompositionLocalProvider(LocalHapticFeedback provides recorder) {
            SwipeToConfirmBox(onConfirm = {}, reveal = {}, modifier = Modifier.testTag(TAG)) {
                Box(Modifier.fillMaxWidth().height(56.dp))
            }
        }
    }

    @Test
    fun `one tick per crossing of the mark, and none on the way back`() {
        show()

        compose.onNodeWithTag(TAG).performTouchInput {
            down(Offset(10f, centerY))
            moveTo(Offset(10f + viewConfiguration.touchSlop + width * 0.1f, centerY))
            moveTo(Offset(10f + viewConfiguration.touchSlop + width * 0.5f, centerY))
            moveTo(Offset(10f + viewConfiguration.touchSlop + width * 0.7f, centerY))
        }
        compose.waitForIdle()
        assertEquals("past the mark", 1, ticks)

        compose.onNodeWithTag(TAG).performTouchInput {
            moveTo(Offset(10f + viewConfiguration.touchSlop + width * 0.1f, centerY))
            moveTo(Offset(10f + viewConfiguration.touchSlop + width * 0.5f, centerY))
            up()
        }
        compose.waitForIdle()
        assertEquals("back, and past it again", 2, ticks)
    }

    @Test
    fun `a drag short of the mark does not tick`() {
        show()

        compose.onNodeWithTag(TAG).performTouchInput {
            down(Offset(10f, centerY))
            moveTo(Offset(10f + viewConfiguration.touchSlop + width * 0.2f, centerY))
            up()
        }
        compose.waitForIdle()

        assertEquals(0, ticks)
    }

    private companion object {
        const val TAG = "row"
    }
}
