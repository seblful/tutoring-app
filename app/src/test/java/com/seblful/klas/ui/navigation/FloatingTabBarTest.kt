package com.seblful.klas.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h740dp")
class FloatingTabBarTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private var tapped: TopDestination? = null
    private var clearance: Dp = 0.dp

    @Test
    fun `every tab is a tab named by its label and only the marked one is selected`() {
        setBar(selected = TopDestination.PEOPLE)

        TopDestination.entries.forEach { dest ->
            val tab = compose.onNode(tabOf(dest) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            if (dest == TopDestination.PEOPLE) tab.assertIsSelected() else tab.assertIsNotSelected()
        }
    }

    @Test
    fun `a tap hands its tab over`() {
        setBar(selected = TopDestination.TODAY)

        compose.onNode(tabOf(TopDestination.REPORTS)).performClick()

        assertEquals(TopDestination.REPORTS, tapped)
    }

    @Test
    fun `the room it reports reaches from the window's foot to the top of the pill`() {
        setBar(selected = TopDestination.TODAY)

        val tabTop = compose.onNode(tabOf(TopDestination.TODAY)).getBoundsInRoot().top
        assertEquals((compose.onRoot().getBoundsInRoot().bottom - tabTop).value, clearance.value, 0.5f)
    }

    private fun setBar(selected: TopDestination) {
        compose.setContent {
            KlasTheme {
                Box(Modifier.fillMaxSize()) {
                    FloatingTabBar(
                        tabs = TopDestination.entries,
                        selected = selected,
                        onSelect = { tapped = it },
                        onClearance = { clearance = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tabOf(dest: TopDestination) = hasText(context.getString(dest.labelRes)) and isSelectable()
}
