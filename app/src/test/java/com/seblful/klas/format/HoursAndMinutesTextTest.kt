package com.seblful.klas.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.testutil.texts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HoursAndMinutesTextTest {

    private val res get() = ApplicationProvider.getApplicationContext<Context>().texts()

    @Test
    @Config(qualifiers = "en")
    fun `a wait reads in hours and minutes rather than a heap of minutes`() {
        assertEquals(said("55 min"), hoursAndMinutesText(res, 55))
        assertEquals(said("1 h"), hoursAndMinutesText(res, 60))
        assertEquals(said("1 h 5 min"), hoursAndMinutesText(res, 65))
        assertEquals(said("4 h 15 min"), hoursAndMinutesText(res, 255))
    }

    @Test
    @Config(qualifiers = "ru")
    fun `in Russian too`() {
        assertEquals(said("40 мин"), hoursAndMinutesText(res, 40))
        assertEquals(said("3 ч"), hoursAndMinutesText(res, 180))
        assertEquals(said("5 ч 30 мин"), hoursAndMinutesText(res, 330))
    }

    @Test
    @Config(qualifiers = "it")
    fun `where the hour's word changes with the number, it agrees`() {
        assertEquals(said("1 ora 10 min"), hoursAndMinutesText(res, 70))
        assertEquals(said("2 ore"), hoursAndMinutesText(res, 120))
    }

    /** Each number stays with its unit across a line break. */
    private fun said(text: String) = text.replace(Regex("(\\d) "), "$1 ")
}
