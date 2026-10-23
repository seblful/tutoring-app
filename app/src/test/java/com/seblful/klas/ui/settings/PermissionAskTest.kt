package com.seblful.klas.ui.settings

import androidx.compose.material3.SnackbarHostState
import com.seblful.klas.PermissionPrompt
import com.seblful.klas.ui.common.Messages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionAskTest {

    private class Recorder(private val due: Boolean) {
        var launches = 0
        val prompt = PermissionPrompt(launch = { launches++ }, rationaleDue = { due })
    }

    private fun TestScope.messages(host: SnackbarHostState) = Messages(host, backgroundScope, "Undo")

    @Test
    fun `a first ask shows the prompt straight away`() = runTest {
        val host = SnackbarHostState()
        val recorder = Recorder(due = false)

        messages(host).ask(recorder.prompt, "why", "Allow")
        runCurrent()

        assertEquals(1, recorder.launches)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `after a refusal the prompt waits for the explanation's action`() = runTest {
        val host = SnackbarHostState()
        val recorder = Recorder(due = true)

        messages(host).ask(recorder.prompt, "why", "Allow")
        runCurrent()

        val shown = host.currentSnackbarData!!
        assertEquals("why", shown.visuals.message)
        assertEquals("Allow", shown.visuals.actionLabel)
        assertEquals("nothing is asked until the tutor taps Allow", 0, recorder.launches)

        shown.performAction()
        runCurrent()
        assertEquals(1, recorder.launches)
    }

    @Test
    fun `an explanation left alone asks nothing`() = runTest {
        val host = SnackbarHostState()
        val recorder = Recorder(due = true)

        messages(host).ask(recorder.prompt, "why", "Allow")
        runCurrent()
        host.currentSnackbarData!!.dismiss()
        runCurrent()

        assertEquals(0, recorder.launches)
    }
}
