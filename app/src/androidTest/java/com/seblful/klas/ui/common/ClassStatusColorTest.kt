package com.seblful.klas.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * A status is read off a lesson by its colour before its words, in a list where several sit at once — so
 * the three have to be three, and a cancelled lesson has to be the one that says "look here".
 *
 * Cancelled was `onSurfaceVariant`, the same grey [sessionStatusColor] gives a lesson with *no* one
 * status («Разные»), so a calendar cell printing `1 1` in grey and grey meant two unrelated things. It is
 * now the theme's error red, and the grey belongs to the mixed lesson alone.
 *
 * Resolved inside [KlasTheme], since these are theme slots rather than fixed values: the assertion is
 * about which slot each status draws from, which is the whole of the decision.
 */
class ClassStatusColorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val statuses = mutableMapOf<ClassStatus, Color>()
    private var mixed = Color.Unspecified
    private var error = Color.Unspecified
    private var neutral = Color.Unspecified

    @Test
    fun cancelledIsRedAndGreyIsLeftToTheMixedLesson() {
        composeRule.setContent {
            KlasTheme {
                ClassStatus.entries.forEach { statuses[it] = classStatusColor(it) }
                mixed = sessionStatusColor(null)
                error = MaterialTheme.colorScheme.error
                neutral = MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        composeRule.waitForIdle()

        assertEquals("a cancelled lesson is not the error red", error, statuses[ClassStatus.CANCELLED])
        assertNotEquals(
            "a cancelled lesson is still the neutral grey, which is the mixed lesson's",
            neutral,
            statuses[ClassStatus.CANCELLED],
        )
        assertEquals("a lesson of no one status is not the neutral grey", neutral, mixed)
        assertEquals(
            "the three statuses do not read as three colours: $statuses",
            ClassStatus.entries.size,
            statuses.values.distinct().size,
        )
    }
}
