package com.seblful.klas.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.ui.theme.KlasColors
import com.seblful.klas.ui.theme.KlasTheme
import com.seblful.klas.ui.theme.klasColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LessonStatusColorTest {

    @get:Rule
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

    @Test
    fun taughtIsGreenInBothSchemesAndTheBadgeReadsIt() {
        lateinit var light: KlasColors
        lateinit var dark: KlasColors
        var taught = Color.Unspecified
        var teal = Color.Unspecified

        composeRule.setContent {
            KlasTheme(darkTheme = false) {
                light = klasColors
                taught = classStatusColor(LessonStatus.COMPLETED)
                teal = MaterialTheme.colorScheme.tertiary
            }
            KlasTheme(darkTheme = true) { dark = klasColors }
        }
        composeRule.waitForIdle()

        assertEquals("«Проведено» is not said in the app's success green", light.success, taught)
        assertNotEquals("«Проведено» is still the palette's teal", teal, taught)
        mapOf("light" to light, "dark" to dark).forEach { (scheme, colors) ->
            mapOf(
                "success" to colors.success,
                "successContainer" to colors.successContainer,
                "onSuccessContainer" to colors.onSuccessContainer,
            ).forEach { (role, color) ->
                assertTrue(
                    "$scheme $role is not a green: $color",
                    color.green > color.red && color.green > color.blue,
                )
            }
        }
    }
}
