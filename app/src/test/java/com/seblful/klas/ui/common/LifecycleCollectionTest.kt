package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleCollectionTest {

    private fun uiSources() =
        ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `screens stop collecting when the app leaves the foreground`() {
        val offenders = uiSources()
            .filter { Regex("""\.collectAsState\(""").containsMatchIn(it.readText()) }
            .map { it.name }

        assertEquals(
            "`collectAsState` keeps collecting while the app is in the background: a destination stays " +
                "composed, so `WhileSubscribed` never stops tickers or whole-table reads. Use " +
                "`collectAsStateWithLifecycle`: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the collections are there to be scanned`() {
        val collections = uiSources().sumOf { it.readText().split(".collectAsStateWithLifecycle(").size - 1 }

        assertTrue("Found almost no lifecycle-aware collections — has the scan stopped matching?", collections >= 20)
    }
}
