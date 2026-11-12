package com.seblful.klas.ui.schedule

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.TestEnvRule
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class LessonPayerDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @get:Rule(order = 0)
    val env = TestEnvRule()

    @Test
    fun `the payer is picked from a row per member and the dialog closes once`() = runBlocking {
        val subject = env.fixtures.subject()
        val first = env.fixtures.pricedStudent("First", subject)
        val second = env.fixtures.pricedStudent("Second", subject)
        val group = env.fixtures.group(subjectId = subject, members = setOf(first, second))
        val ids = env.fixtures.completedLesson(
            subject, listOf(first, second), Instant.parse("2026-05-01T10:00:00Z"), groupId = group,
        )
        val row = ScheduleRow(env.classRepo.getSessionFor(ids.first())!!, "Group", "Maths", listOf("First", "Second"))
        var payer: Long? = null
        var dismissed = 0
        compose.setContent {
            KlasTheme { LessonPayerDialog(row, onSelect = { payer = it }, onDismiss = { dismissed++ }) }
        }

        compose.onNodeWithText("First").assertHasClickAction()
        compose.onNodeWithText("Second").performClick()

        assertEquals(second, payer)
        assertEquals(1, dismissed)
    }
}
