package com.seblful.klas.ui.common

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.pressBack
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.Strings
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun keyboardHeight(): Int = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

    // The inset animates in; scrolling to the end before it finishes lands short of the final padding.
    private fun settleKeyboard() = compose.waitUntil(10_000) {
        val before = keyboardHeight()
        Thread.sleep(400)
        before > 0 && before == keyboardHeight()
    }

    @Test
    fun theLastResultClearsTheKeyboardAndBackClosesSearchBeforeLeaving() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent {
            var query by remember { mutableStateOf("") }
            KlasTheme {
                KlasScreen("People", search = ScreenSearch(query, "Find", { query = it })) { modifier, bottom ->
                    LazyColumn(modifier.testTag("results"), contentPadding = bottom) {
                        items(45) { index -> Text("Student $index", Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(compose.activity.texts().string(Strings.action_search)).performClick()
        compose.waitUntil(10_000) { keyboardHeight() > 0 }
        settleKeyboard()
        compose.onNodeWithTag("results").performScrollToIndex(44)
        compose.waitForIdle()
        val bottom = compose.onNodeWithText("Student 44").fetchSemanticsNode().boundsInWindow.bottom
        var keyboardTop = 0
        compose.runOnIdle { keyboardTop = compose.activity.window.decorView.height - keyboardHeight() }
        assertTrue("The final result is hidden behind the keyboard: $bottom > $keyboardTop", bottom <= keyboardTop + 1)

        pressBack()
        compose.waitUntil(10_000) { keyboardHeight() == 0 }
        compose.onNodeWithContentDescription(compose.activity.texts().string(Strings.search_close)).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("People").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.texts().string(Strings.search_close)).assertDoesNotExist()
        assertFalse(compose.activity.isFinishing)
    }
}
