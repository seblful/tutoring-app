package com.seblful.klas.ui.homework

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NestedHomeworkSwipeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a student's homework row covers its own edges as soon as a drag starts`() {
        var page = Color.Unspecified
        compose.setContent {
            KlasTheme {
                page = ListItemDefaults.containerColor
                SwipeableHomework(
                    row = row,
                    onSwipe = {},
                    onClick = {},
                    messages = rememberMessages(),
                    nested = true,
                    modifier = Modifier.testTag("row").width(360.dp),
                )
            }
        }

        // Just past the slop: the row has moved a pixel or two, far less than its 8 dp side margins.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("row").performTouchInput {
            down(center)
            moveBy(Offset(viewConfiguration.touchSlop + 2f, 0f))
        }
        compose.mainClock.advanceTimeByFrame()

        // Drawn in software rather than through `captureToImage`, whose PixelCopy never completes under
        // Robolectric while a finger is held down.
        val screen = compose.activity.window.decorView.drawToBitmap()
        val bounds = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInWindow
        val margin = 4 * compose.density.density
        val middle = bounds.center.y.roundToInt()
        val left = screen.getPixel((bounds.left + margin + 2).roundToInt(), middle)
        val right = screen.getPixel((bounds.right - margin).roundToInt(), middle)
        assertEquals("inside the left margin", page.toArgb(), left)
        assertEquals("inside the right margin", page.toArgb(), right)
    }

    @Test
    fun `done homework is swiped back toward the current tab, leaving the other way to the page`() {
        var restored = 0
        compose.setContent {
            KlasTheme {
                SwipeableHomework(
                    row = row.copy(homework = row.homework.copy(doneAt = Instant.fromEpochMilliseconds(1))),
                    onSwipe = { restored++ },
                    onClick = {},
                    messages = rememberMessages(),
                    nested = false,
                    modifier = Modifier.testTag("row").width(360.dp),
                )
            }
        }

        compose.onNodeWithTag("row").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals("left to right turns the page", 0, restored)

        compose.onNodeWithTag("row").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, restored)
    }

    private val row = HomeworkRow(
        homework = Homework(
            id = 1,
            assignee = Assignee.Student(1),
            text = "Законы Ньютона: задачи 5.12–5.20",
            givenAt = Instant.fromEpochMilliseconds(0),
        ),
        title = "Максим Ковалёв",
        lessonAt = Instant.fromEpochMilliseconds(0),
        due = HomeworkRules.Due.OVERDUE,
    )
}
