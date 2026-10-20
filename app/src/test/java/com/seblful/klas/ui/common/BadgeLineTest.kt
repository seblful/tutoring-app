package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BadgeLineTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a pill in a row's second line keeps the row two lines tall`() {
        compose.setContent {
            KlasTheme {
                Column {
                    Row("plain") { Text("Индивидуальное") }
                    Row("badged") {
                        BadgeLine(badge = { StatusBadge("Проведено", Color.Green) }) { Text("Индивидуальное") }
                    }
                }
            }
        }

        assertEquals(height("plain"), height("badged"))
    }

    @Composable
    private fun Row(tag: String, supporting: @Composable () -> Unit) {
        ListItem(
            leadingContent = { Icon(KlasIcons.Filled.Person, contentDescription = null) },
            headlineContent = { Text("3 ноя, 16:00 · Физика") },
            supportingContent = supporting,
            modifier = Modifier.testTag(tag),
        )
    }

    private fun height(tag: String): Int = compose.onNodeWithTag(tag).fetchSemanticsNode().size.height
}
