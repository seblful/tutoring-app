package com.seblful.klas.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.di.TimeModule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class DeviceTimeChangesTest {
    @Test
    fun `an injected clock follows a device zone change without being recreated`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Minsk"))
            val clock = TimeModule.provideClock(ApplicationProvider.getApplicationContext<Context>())
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            assertEquals(ZoneId.of("America/New_York"), clock.zone)
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
