package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A card takes its tap through its own `onClick`, never through a `clickable` modifier.**
 *
 * A `clickable` in the modifier handed *to* a card is applied outside the card's clip, so the press
 * ripple is drawn as a hard-cornered rectangle across the whole item — over and past the rounded
 * corners. [ContentCard] had it, and every tap on a lesson in the schedule flashed a grey frame
 * behind the card. Nothing about the code looks wrong: the ripple appears only while a finger is
 * down, and the same modifier chain on a full-bleed row is exactly right.
 *
 * The inset rows in this app already spell the alternative — `clip(shape).clickable(…)`, so the
 * indication is bounded by the shape it belongs to — but a card has no reason to reach for either:
 * `Card(onClick = …)` draws its own indication inside its own clip.
 *
 * Only the *argument list* is scanned, not the trailing content lambda: a control drawn inside a card
 * has its own bounds and may be clickable.
 */
class CardPressFeedbackTest {

    private fun uiSources() =
        ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `no card takes its click from a clickable modifier`() {
        val offenders = uiSources()
            .filter { file ->
                argumentLists(file.readText(), "Card(").any { "clickable" in it }
            }
            .map { it.name }

        assertEquals(
            "A `clickable` is passed to a Card, which draws its ripple outside the card's clip — a " +
                "hard-cornered rectangle over the rounded corners. Use `Card(onClick = …)`: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /** So the test above cannot pass by there being no cards left to get this wrong. */
    @Test
    fun `the cards are there to be scanned`() {
        val cards = uiSources().sumOf { argumentLists(it.readText(), "Card(").size }

        assertTrue("Found no `Card(` argument lists at all — has the scan stopped matching?", cards >= 5)
    }

    /**
     * Every `callee(…)` argument list in [code], by paren depth — which stops at the closing paren, so
     * a trailing lambda is left out. Matches on the suffix, so `OutlinedCard(` and this app's own
     * `ContentCard(`/`SectionCard(` call sites are scanned too: a `clickable` handed to a wrapper is
     * unclipped for the same reason as one handed to the card inside it.
     */
    private fun argumentLists(code: String, callee: String): List<String> {
        val lists = mutableListOf<String>()
        var from = code.indexOf(callee)
        while (from >= 0) {
            val open = from + callee.length - 1
            var depth = 0
            var i = open
            while (i < code.length) {
                if (code[i] == '(') depth++
                if (code[i] == ')') {
                    depth--
                    if (depth == 0) break
                }
                i++
            }
            lists += code.substring(open + 1, i.coerceAtMost(code.length))
            from = code.indexOf(callee, i)
        }
        return lists
    }
}
