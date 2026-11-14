package com.seblful.klas.ui.pro

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.pro.ProSource
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class OwnedProTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tap on the manage row's words opens the subscription`() {
        var opened = 0
        // In a column, as on ProScreen: Owned is two cards, one after the other.
        compose.setContent { KlasTheme { Column { Owned(ProSource.MONTHLY, onManage = { opened++ }) } } }

        // Only its trailing icon took the tap once, so a tutor tapping the words got nothing.
        compose.onNodeWithText("Manage subscription").performClick()
        compose.waitForIdle()

        assertEquals(1, opened)
    }
}
