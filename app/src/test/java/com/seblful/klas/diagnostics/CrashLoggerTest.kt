package com.seblful.klas.diagnostics

import com.seblful.klas.testutil.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class FakeCrashLogStore(var failing: Boolean = false) : CrashLogStore {
    var content: String = ""
    var writes = 0

    override fun read(): String {
        if (failing) error("cannot read")
        return content
    }

    override fun write(text: String) {
        if (failing) error("cannot write")
        writes++
        content = text
    }

    override fun delete() {
        content = ""
    }

    override fun isEmpty(): Boolean = content.isEmpty()
}

class CrashLoggerTest {

    private val store = FakeCrashLogStore()
    private val device = DeviceInfo(appVersion = "1.2.3", androidSdk = 34, device = "Pixel 8")
    private val logger = CrashLogger(store, device, MutableClock(Instant.parse("2026-01-01T00:00:00Z")))
    private val at = Instant.parse("2026-06-15T09:30:00Z")

    @Test
    fun `a recorded crash carries the build and device facts and the stack trace`() {
        logger.record(IllegalStateException("boom"), at)

        val log = store.content
        assertTrue("names the app version", log.contains("1.2.3"))
        assertTrue("names the SDK level", log.contains("34"))
        assertTrue("names the device", log.contains("Pixel 8"))
        assertTrue("carries the exception", log.contains("boom"))
        assertTrue("carries the type", log.contains("IllegalStateException"))
        assertTrue("timestamped", log.contains(at.toString()))
    }

    @Test
    fun `crashes accumulate, newest last`() {
        logger.record(RuntimeException("first"), at)
        logger.record(RuntimeException("second"), at.plus(60.seconds))

        val log = store.content
        assertTrue(log.indexOf("first") < log.indexOf("second"))
        assertEquals(2, log.split(Diagnostics.ENTRY_MARKER).size - 1)
    }

    @Test
    fun `a crash loop cannot grow the log without bound`() {
        repeat(200) { logger.record(RuntimeException("crash $it"), at.plus((it.toLong()).seconds)) }

        assertTrue(
            "capped at ${Diagnostics.MAX_LOG_CHARS}, was ${store.content.length}",
            store.content.length <= Diagnostics.MAX_LOG_CHARS,
        )
        assertTrue("keeps the most recent", store.content.contains("crash 199"))
        assertFalse("drops the oldest", store.content.contains("crash 0\n"))
    }

    @Test
    fun `hasLog reflects whether anything was recorded`() {
        assertFalse(logger.hasLog())
        logger.record(RuntimeException("boom"), at)
        assertTrue(logger.hasLog())
        logger.clear()
        assertFalse(logger.hasLog())
        assertEquals("", logger.read())
    }

    @Test
    fun `the installed handler records the crash and then hands off to the previous one`() {
        val previous = RecordingHandler()
        val restore = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            val handler = logger.install()

            val thread = Thread.currentThread()
            val boom = RuntimeException("boom")
            handler.uncaughtException(thread, boom)

            assertTrue("recorded", store.content.contains("boom"))
            assertSame("chained, so Play still reports it", boom, previous.throwable)
            assertSame(thread, previous.thread)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(restore)
        }
    }

    @Test
    fun `a failure while logging never masks the crash`() {
        store.failing = true
        val previous = RecordingHandler()
        val restore = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            val handler = logger.install()

            handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertEquals("nothing was written", 0, store.writes)
            assertEquals("boom", previous.throwable?.message)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(restore)
        }
    }

    @Test
    fun `installing with no previous handler still records`() {
        val restore = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(null)
            val handler = logger.install()

            handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertTrue(store.content.contains("boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(restore)
        }
    }

    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        var thread: Thread? = null
        var throwable: Throwable? = null

        override fun uncaughtException(t: Thread, e: Throwable) {
            thread = t
            throwable = e
        }
    }
}
