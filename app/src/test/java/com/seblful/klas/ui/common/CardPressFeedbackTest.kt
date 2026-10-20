package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText.argumentLists
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `the cards are there to be scanned`() {
        val cards = uiSources().sumOf { argumentLists(it.readText(), "Card(").size }

        assertTrue("Found no `Card(` argument lists at all — has the scan stopped matching?", cards >= 5)
    }
}
