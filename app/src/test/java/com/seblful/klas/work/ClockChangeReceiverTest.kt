package com.seblful.klas.work

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockChangeReceiverTest {

    @Test
    fun `a timezone or clock change re-plans, anything else does not`() {
        assertTrue(ClockChangeReceiver.handles("android.intent.action.TIMEZONE_CHANGED"))
        assertTrue(ClockChangeReceiver.handles("android.intent.action.TIME_SET"))
        assertFalse(ClockChangeReceiver.handles("android.intent.action.BOOT_COMPLETED"))
        assertFalse(ClockChangeReceiver.handles(null))
    }

    /** A receiver the manifest does not register for an action never hears it — the quiet failure. */
    @Test
    fun `the manifest registers the receiver for every action it handles`() {
        val manifest = ProjectFiles.moduleFile("src/main/AndroidManifest.xml").readText()
        val block = manifest.substringAfter("android:name=\".work.ClockChangeReceiver\"").substringBefore("</receiver>")

        ClockChangeReceiver.ACTIONS.forEach { action ->
            assertTrue("$action is handled but not registered", "android:name=\"$action\"" in block)
        }
    }
}
