package com.seblful.klas.ui.students

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.queries.students.SubjectPrice
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class SubjectRowTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val physics = Subject(id = 1, name = "Physics")
    private val owner = RateOwner.StudentSubject(studentId = 1, subjectId = 1)
    private val sep1 = Instant.parse("2026-09-01T09:00:00Z")

    @Test
    fun `the rate trails, its last change is said beneath, and a tap opens it`() {
        var opened = 0
        val history = listOf(
            RateChange(owner = owner, from = Money.ofMajor(20), to = Money.ofMajor(25), at = sep1),
            RateChange(owner = owner, from = null, to = Money.ofMajor(20), at = Instant.parse("2026-01-10T09:00:00Z")),
        )
        compose.setContent {
            KlasTheme { SubjectRow(SubjectPrice(physics, Money.ofMajor(25), history), onOpen = { opened++ }) }
        }

        compose.onNodeWithText("25", substring = true).assertExists()
        compose.onNodeWithText("was", substring = true).assertExists()
        compose.onNodeWithText("Physics").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `a rate that never changed says nothing beneath`() {
        val history = listOf(RateChange(owner = owner, from = null, to = Money.ofMajor(25), at = null))
        compose.setContent {
            KlasTheme { SubjectRow(SubjectPrice(physics, Money.ofMajor(25), history), onOpen = {}) }
        }

        compose.onNodeWithText("was", substring = true).assertDoesNotExist()
    }
}
