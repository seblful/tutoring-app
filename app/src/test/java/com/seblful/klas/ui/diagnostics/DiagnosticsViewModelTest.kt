package com.seblful.klas.ui.diagnostics

import com.seblful.klas.diagnostics.CrashLogStore
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.diagnostics.DeviceInfo
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

private class FakeCrashLogStore : CrashLogStore {
    var content: String = ""
    override fun read(): String = content
    override fun write(text: String) { content = text }
    override fun delete() { content = "" }
    override fun isEmpty(): Boolean = content.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    private val store = FakeCrashLogStore()
    private val documents = FakeDocumentStore()
    private val crashLogger = CrashLogger(
        store,
        DeviceInfo("1.0", "Android SDK 34", "Pixel"),
        com.seblful.klas.testutil.MutableClock(Instant.parse("2026-01-01T00:00:00Z")),
    )
    private val target = DocumentRef("content://downloads/crash.txt")

    private fun vm(isDebugBuild: Boolean = false) =
        mainRule.own(DiagnosticsViewModel(crashLogger, documents, isDebugBuild))

    @Test
    fun `state reports an empty log on a clean install`() {
        val vm = vm()
        assertFalse(vm.state.value.hasLog)
        assertEquals("", vm.state.value.log)
    }

    @Test
    fun `state carries the recorded log`() {
        crashLogger.record(RuntimeException("boom"), Instant.parse("2026-06-15T09:00:00Z"))

        val vm = vm()

        assertTrue(vm.state.value.hasLog)
        assertTrue(vm.state.value.log.contains("boom"))
    }

    @Test
    fun `export writes the log to the picked document and reports success`() =
        runTest(mainRule.dispatcher) {
            crashLogger.record(RuntimeException("boom"), Instant.parse("2026-06-15T09:00:00Z"))
            val vm = vm()

            vm.export(target)
            advanceUntilIdle()

            assertTrue(documents.docs.getValue(target.value).contains("boom"))
            assertEquals(DiagnosticsEvent.EXPORT_OK, vm.events.stream.first())
        }

    @Test
    fun `export reports an error when the document cannot be written`() =
        runTest(mainRule.dispatcher) {
            documents.failing = true
            val vm = vm()

            vm.export(target)
            advanceUntilIdle()

            assertEquals(DiagnosticsEvent.ERROR, vm.events.stream.first())
            assertTrue(documents.docs.isEmpty())
        }

    @Test
    fun `clearing empties the log and the state follows`() {
        crashLogger.record(RuntimeException("boom"), Instant.parse("2026-06-15T09:00:00Z"))
        val vm = vm()
        assertTrue(vm.state.value.hasLog)

        vm.clear()

        assertFalse(vm.state.value.hasLog)
        assertEquals("", vm.state.value.log)
    }

    @Test
    fun `the test-crash action is offered only on a debuggable build`() {
        assertFalse("never in a release build", vm(isDebugBuild = false).isDebugBuild)
        assertTrue(vm(isDebugBuild = true).isDebugBuild)
    }
}
