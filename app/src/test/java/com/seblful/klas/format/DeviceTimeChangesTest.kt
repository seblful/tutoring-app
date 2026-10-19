package com.seblful.klas.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.di.TimeModule
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceTimeChangesTest {
    @Test
    fun `an injected clock follows a device zone change without being recreated`() {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Minsk"))
            val clock = TimeModule.provideClock(ApplicationProvider.getApplicationContext<Context>())
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))

            assertEquals(TimeZone.of("America/New_York"), clock.zone)
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }
}
