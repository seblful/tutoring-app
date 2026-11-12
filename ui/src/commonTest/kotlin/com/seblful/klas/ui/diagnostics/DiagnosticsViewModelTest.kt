package com.seblful.klas.ui.diagnostics

import com.seblful.klas.diagnostics.CrashLogStore
import com.seblful.klas.diagnostics.CrashLogger
import com.seblful.klas.diagnostics.DeviceInfo
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeCrashLogStore : CrashLogStore {
    var content: String = ""
    override fun read(): String = content
    override fun write(text: String) { content = text }
    override fun delete() { content = "" }
    override fun isEmpty(): Boolean = content.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMain() = main.finish()

    private val main = TestMain()

    private val store = FakeCrashLogStore()
    private val documents = FakeDocumentStore()
    private val crashLogger = CrashLogger(
        store,
        DeviceInfo("1.0", "Android SDK 34", "Pixel"),
        com.seblful.klas.testutil.MutableClock(Instant.parse("2026-01-01T00:00:00Z")),
    )
    private val target = DocumentRef("content://downloads/crash.txt")

    private fun vm(isDebugBuild: Boolean = false) =
        main.own(DiagnosticsViewModel(crashLogger, documents, isDebugBuild))

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
        runTest(main.dispatcher) {
            crashLogger.record(RuntimeException("boom"), Instant.parse("2026-06-15T09:00:00Z"))
            val vm = vm()

            vm.export(target)
            advanceUntilIdle()

            assertTrue(documents.docs.getValue(target.value).contains("boom"))
            assertEquals(DiagnosticsEvent.EXPORT_OK, vm.events.stream.first())
        }

    @Test
    fun `export reports an error when the document cannot be written`() =
        runTest(main.dispatcher) {
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
        assertFalse(vm(isDebugBuild = false).isDebugBuild, "never in a release build")
        assertTrue(vm(isDebugBuild = true).isDebugBuild)
    }
}
