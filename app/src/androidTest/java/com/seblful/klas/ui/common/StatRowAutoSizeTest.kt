package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.testutil.textLayoutOf
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A stat tile's number has to fit the tile, and never be drawn larger than the scale asks for.
 *
 * The counterpart of `BottomBarLabelTest` for the other place in the app that autosizes: a tile is a
 * fixed *fraction* of the screen, so the widest amount the app will accept has to survive being put in
 * a third of a 320dp phone. The row's own rule is that a value shrinks rather than wrapping or being
 * cut, and neither half of that follows from asking for `TextAutoSize` — the minimum can be too large
 * to fit, and the maximum can be missing, which draws a short value *bigger* than its neighbours.
 *
 * Written when the app was moved off the platform typeface onto Golos Text, which is the change these
 * assertions exist for: every one of them is about how wide real glyphs come out, so swapping the face
 * is precisely the edit that can break them, and nothing was measuring it. They hold for any face — it
 * is the *row* being specified here, not the font.
 *
 * Font scale is pinned to 1 throughout, as in the bottom bar's suite: these are assertions about the
 * layout, and the device's own font setting would otherwise decide whether they hold.
 */
class StatRowAutoSizeTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * The worst case the app can actually produce, and every part of it is deliberate.
     *
     * **Three** tiles because that is the most any screen builds (a group's card: lessons, hours,
     * earned) — the row accepts more, but a fourth tile is not a case the app can reach, and a test that
     * failed on one would be reporting a hypothetical. The amount is `Money.MAX` formatted, the largest
     * value that can exist: `parseMoneyOrNull` refuses anything above 1 000 000,00, so this is the widest
     * string a stat tile will ever be handed rather than a pessimistic invention. And the hint is there
     * because a student's card puts one under the lesson count, which makes the tile three lines tall.
     */
    private val stats = listOf(
        Stat(label = "Занятий", value = "128", hint = "впереди 3"),
        Stat(label = "Часов", value = "192,5"),
        Stat(label = "Заработано", value = "1000000,00 BYN"),
    )

    /**
     * Nothing in the row wraps or is cut, at any phone width.
     *
     * Both halves matter and they fail differently. A wrapped value would shove the row's tiles to
     * different heights — the tiles share a baseline by sitting in one `Row`, not by agreeing to — and a
     * cut one is the failure the shrinking exists to avoid: «1000000,0…» is a number that has lost its
     * meaning, where a small «1000000,00 BYN» is still exactly right.
     *
     * The labels are checked too, though they are *not* autosized — they are one-line ellipsising text.
     * That makes them the more fragile half: a value that no longer fits gets smaller, while a label that
     * no longer fits is simply cut. If one ever trips this, the fix is the label or the tile and not the
     * autosize.
     */
    @Test
    fun everyTileFitsAtEveryPhoneWidth() {
        val width = mutableStateOf(Widths.first())
        composeRule.setStatContent(width)

        // Collected rather than asserted one at a time: which width a tile misbehaves at is the
        // interesting part, and the first failure would otherwise hide the rest.
        val complaints = mutableListOf<String>()
        Widths.forEach { candidate ->
            composeRule.runOnIdle { width.value = candidate }
            composeRule.waitForIdle()
            stats.flatMap { listOfNotNull(it.value, it.label, it.hint) }.forEach { text ->
                val layout = composeRule.textLayoutOf(text)
                if (layout.lineCount != 1) {
                    complaints += "«$text» takes ${layout.lineCount} lines at $candidate"
                }
                if (layout.isLineEllipsized(0)) complaints += "«$text» is cut short at $candidate"
            }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    /**
     * The cap on the autosizing, which is the half of it that is easy to get wrong.
     *
     * `TextAutoSize` enlarges as readily as it shrinks, so without a maximum the short value in a wide
     * tile would be drawn *bigger* than `titleMedium` asks for — «128» looming over the «1000000,00 BYN»
     * beside it, in a row whose whole point is that three numbers read as one set. Asserted on the
     * shortest value, which fits at every width by a wide margin, so what is being measured is the cap
     * and not how much room the row happened to have.
     */
    @Test
    fun aValueWithRoomToSpareIsNeverEnlarged() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setStatContent(width)
        val narrow = composeRule.textLayoutOf(SHORT_VALUE).size.width

        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()
        val large = composeRule.textLayoutOf(SHORT_VALUE).size.width

        assertEquals(
            "«$SHORT_VALUE» fits either way, so it must be drawn at the same size either way",
            large,
            narrow,
        )
    }

    /**
     * And the floor is doing something: the widest amount **is** drawn smaller where the tile is tighter.
     *
     * Without this the suite would pass on a row that had quietly stopped autosizing at all — every
     * assertion above is also satisfied by text that happens to fit everywhere. A strict inequality is
     * safe here precisely because the amount is the app's maximum: at 320dp a third of the screen cannot
     * hold it at 16sp, and at 411dp there is more room, so the two must differ.
     */
    @Test
    fun theWidestAmountShrinksWhereTheTileIsTighter() {
        val width = mutableStateOf(NarrowPhone)
        composeRule.setStatContent(width)
        val narrow = composeRule.textLayoutOf(WIDEST_VALUE).size.width

        composeRule.runOnIdle { width.value = LargePhone }
        composeRule.waitForIdle()
        val large = composeRule.textLayoutOf(WIDEST_VALUE).size.width

        assertTrue(
            "«$WIDEST_VALUE» is ${narrow}px on a $NarrowPhone screen and ${large}px on a " +
                "$LargePhone one — it is not shrinking to fit the narrow tile",
            narrow < large,
        )
    }

    /** The row constrained to a phone's width, with the font scale pinned. */
    private fun ComposeContentTestRule.setStatContent(width: MutableState<Dp>) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1f),
            ) {
                KlasTheme {
                    Box(Modifier.width(width.value)) {
                        StatRow(stats)
                    }
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        /** Narrowest phone width still supported, the common one, and a large one. */
        val NarrowPhone = 320.dp
        val LargePhone = 411.dp
        val Widths = listOf(NarrowPhone, 360.dp, LargePhone)

        const val SHORT_VALUE = "128"
        const val WIDEST_VALUE = "1000000,00 BYN"
    }
}
