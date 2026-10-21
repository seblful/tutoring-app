package com.seblful.klas.ui.common

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import com.seblful.klas.testutil.AppLocaleRule
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ProvideClockFormat]: the phone's 12- or 24-hour clock, asked once and again on coming back. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class ClockFormatProvisionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val language = AppLocaleRule.english()

    private fun phoneClock(hours: String) =
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, hours)

    @Test
    fun `times follow the phone's clock, changed while the app was away`() {
        phoneClock("24")
        compose.setContent { ProvideClockFormat { Text(LocalTime(21, 0).timeText()) } }
        compose.onNodeWithText("21:00").assertExists()

        // Changed in the phone's settings, which takes the app off the screen and brings it back.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        phoneClock("12")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.onNodeWithText("9:00", substring = true).assertExists()
    }
}
