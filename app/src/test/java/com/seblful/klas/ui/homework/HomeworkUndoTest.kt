package com.seblful.klas.ui.homework

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.ui.common.rememberMessages
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/** [homeworkUndo] as the lists use it: captured by every card's swipe. */
@RunWith(RobolectricTestRunner::class)
class HomeworkUndoTest {

    @get:Rule
    val compose = createComposeRule()

    private val item =
        Homework(id = 1, assignee = Assignee.Student(1), text = "p. 12", givenAt = Instant.fromEpochSeconds(0))

    @Test
    fun `the screen recomposing keeps one undo, which writes through the newest callback`() {
        var pass by mutableIntStateOf(0)
        val undos = mutableListOf<HomeworkUndo>()
        val writes = mutableListOf<Int>()
        compose.setContent {
            // A new lambda each pass, as a screen's callbacks may be.
            val seen = pass
            undos += homeworkUndo(
                messages = rememberMessages(),
                setDone = { _, _ -> writes += seen },
                putBack = {},
                delete = {},
                restore = {},
            )
        }

        compose.runOnIdle { pass = 1 }
        compose.runOnIdle { pass = 2 }
        compose.runOnIdle { undos.last().swiped(item, done = true) }

        assertEquals("passes composed", 3, undos.size)
        assertEquals("distinct undos", 1, undos.distinct().size)
        assertEquals(listOf(2), writes)
    }
}
