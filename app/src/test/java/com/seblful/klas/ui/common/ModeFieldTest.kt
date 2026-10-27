package com.seblful.klas.ui.common

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A student's or group's place: both answers in sight, as booking's Place segments are, never behind a ▾. */
@RunWith(RobolectricTestRunner::class)
class ModeFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val inPerson: String = resources.getString(R.string.class_mode_in_person)
    private val remote: String = resources.getString(R.string.class_mode_remote)

    private var chosen: ClassMode? = null

    private fun open(selected: ClassMode) {
        rule.setContent {
            KlasTheme {
                ModeField(
                    label = stringResource(R.string.class_location_label),
                    selected = selected,
                    onSelect = { mode -> chosen = mode },
                )
            }
        }
    }

    @Test
    fun `both places are in sight and the chosen one is marked`() {
        open(selected = ClassMode.REMOTE)

        rule.onNodeWithText(remote).assertIsSelected()
        rule.onNodeWithText(inPerson).assertIsDisplayed()
        assertNull("nothing was picked, so nothing was reported", chosen)
    }

    @Test
    fun `a tap on the other place picks it`() {
        open(selected = ClassMode.IN_PERSON)

        rule.onNodeWithText(remote).performClick()

        assertEquals(ClassMode.REMOTE, chosen)
    }
}
