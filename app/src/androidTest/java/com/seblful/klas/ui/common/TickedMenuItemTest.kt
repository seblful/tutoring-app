package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test

class TickedMenuItemTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun onlyTheEntryInForceIsSelected() {
        rule.setContent {
            KlasTheme {
                Column {
                    TickedMenuItem(text = BY_NAME, selected = true, onClick = {})
                    TickedMenuItem(text = BY_DEBT, selected = false, onClick = {})
                }
            }
        }

        rule.onNodeWithText(BY_NAME).assertIsSelected()
        rule.onNodeWithText(BY_DEBT).assertIsNotSelected()
    }

    private companion object {
        const val BY_NAME = "По имени"
        const val BY_DEBT = "По долгу"
    }
}
