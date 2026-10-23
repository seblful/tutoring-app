package com.seblful.klas.ui.common

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesTest {

    @Test
    fun `an undoable change is said with the one Undo, which puts it back`() = runTest {
        val host = SnackbarHostState()
        var undone = 0

        Messages(host, backgroundScope, "Undo").undoable("Payment deleted") { undone++ }
        runCurrent()

        val shown = host.currentSnackbarData!!
        assertEquals("Payment deleted", shown.visuals.message)
        assertEquals("Undo", shown.visuals.actionLabel)
        shown.performAction()
        runCurrent()
        assertEquals(1, undone)
    }
}
