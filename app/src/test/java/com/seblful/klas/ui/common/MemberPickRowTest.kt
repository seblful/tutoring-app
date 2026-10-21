package com.seblful.klas.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.Student
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemberPickRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the whole row is one checkbox that ticks and unticks the member`() {
        var picked by mutableStateOf(false)
        val toggles = mutableListOf<Boolean>()
        compose.setContent {
            KlasTheme {
                MemberPickRow(
                    Student(id = 7, name = "Anna"),
                    selected = picked,
                    onToggle = { picked = it; toggles += it },
                )
            }
        }

        compose.onNodeWithText("Anna")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        compose.onNodeWithText("Anna")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .performClick()

        assertEquals(listOf(true, false), toggles)
    }
}
