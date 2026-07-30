package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * What a monogram and a stack of them **say out loud**.
 *
 * Both are pictures built out of text, which is the whole reason they needed fixing: a letter drawn to
 * stand for a name is indistinguishable, to a screen reader, from a letter that *is* the content. So the
 * students list announced «И» and then «Ирина Петрова» — the same fact twice, the first time as a word
 * that means nothing — and a group of six read as four unrelated letters and «+2», with nothing saying
 * they were people.
 *
 * Asserted through the semantics tree rather than by reading the source, because what is being specified
 * is what TalkBack receives, and that is the product of the modifiers, the merging and the children
 * together. A tripwire on `clearAndSetSemantics` would pass on a call that cleared the wrong node.
 *
 * ### Read the merged tree, which is the one that is spoken
 *
 * Every assertion below is on the **merged** tree, and that is the whole trick. `clearAndSetSemantics`
 * does not delete its descendants — it marks itself as clearing, and the *merged* traversal then stops
 * there. The unmerged tree still holds the monogram's `Text`, exactly as it still holds the pixels, so
 * `useUnmergedTree = true` here would be asserting against a tree no accessibility service ever reads:
 * it fails on a correct implementation. (It did — that is why this note exists.)
 */
@HiltAndroidTest
class AvatarSemanticsTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The monogram is silent, and the name beside it is not.
     *
     * Both halves are the assertion. Clearing the circle's semantics is easy to over-apply — a
     * `clearAndSetSemantics` on the wrong ancestor would take the name with it — so the row is drawn the
     * way a list row draws it, name and all, and the name is required to survive.
     */
    @Test
    fun aMonogramIsNotReadOutBesideTheNameItStandsFor() {
        composeRule.setContent {
            KlasTheme {
                Row {
                    Avatar(name = "Ирина", studentId = 1)
                    Text("Ирина Петрова")
                }
            }
        }

        composeRule.onNodeWithText("Ирина Петрова").assertIsDisplayed()
        // The letter is still drawn; it is simply no longer reachable in the tree that gets spoken.
        composeRule.onNodeWithText("И").assertDoesNotExist()
    }

    /**
     * A stack of six names is announced as six names.
     *
     * The count is chosen to exceed the four faces the stack draws, so this pins the decision that the
     * description is not the *drawn* roster but the whole one: «+2» is what the row's width can afford,
     * and it must not become what a screen reader is told.
     */
    @Test
    fun aStackIsAnnouncedAsItsWholeRosterRatherThanItsVisibleFaces() {
        val names = listOf("Аня", "Борис", "Вера", "Галина", "Дмитрий", "Егор")
        composeRule.setContent {
            KlasTheme {
                AvatarStack(names = names, ids = names.indices.map { it.toLong() })
            }
        }

        val expected = context.getString(R.string.roster_members, names.joinToString(", "))
        composeRule.onNodeWithContentDescription(expected).assertExists()
        // The overflow badge is drawn but not spoken — the two members it stands for are named above.
        composeRule.onNodeWithText("+2").assertDoesNotExist()
    }
}
