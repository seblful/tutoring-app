package com.seblful.klas.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.ui.theme.KlasTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * A status is read off a lesson by its colour before its words, in a list where several sit at once — so
 * the three have to be three, and a cancelled lesson has to be the one that says "look here".
 *
 * Cancelled was `onSurfaceVariant`, the same grey a `sessionStatusColor` gave a group lesson whose
 * members' statuses disagreed («Разные»), so a calendar cell printing `1 1` in grey and grey meant two
 * unrelated things. Cancelled is now the theme's error red — and the other grey is gone with the badge
 * that used it, a lesson answering with one of these three however its members were corrected.
 *
 * Resolved inside [KlasTheme], since these are theme slots rather than fixed values: the assertion is
 * about which slot each status draws from, which is the whole of the decision.
 */
@HiltAndroidTest
class LessonStatusColorTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val statuses = mutableMapOf<LessonStatus, Color>()
    private var error = Color.Unspecified
    private var neutral = Color.Unspecified

    @Test
    fun cancelledIsTheErrorRedAndNoStatusIsGrey() {
        composeRule.setContent {
            KlasTheme {
                LessonStatus.entries.forEach { statuses[it] = classStatusColor(it) }
                error = MaterialTheme.colorScheme.error
                neutral = MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        composeRule.waitForIdle()

        assertEquals("a cancelled lesson is not the error red", error, statuses[LessonStatus.CANCELLED])
        assertEquals(
            "the three statuses do not read as three colours: $statuses",
            LessonStatus.entries.size,
            statuses.values.distinct().size,
        )
        statuses.forEach { (status, color) ->
            assertNotEquals("$status is drawn in the neutral grey, which is nobody's status", neutral, color)
        }
    }
}
