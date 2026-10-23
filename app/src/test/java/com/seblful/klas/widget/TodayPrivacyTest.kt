package com.seblful.klas.widget

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
import com.seblful.klas.data.settings.Privacy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPrivacyTest {

    @Test
    fun `a home screen always lists the day's lessons`() {
        assertTrue(listsLessons(WIDGET_CATEGORY_HOME_SCREEN, Privacy()))
    }

    @Test
    fun `the lock screen lists them only when the tutor chose so`() {
        assertFalse(listsLessons(WIDGET_CATEGORY_KEYGUARD, Privacy()))
        assertTrue(listsLessons(WIDGET_CATEGORY_KEYGUARD, Privacy(lockScreenWidgets = true)))
    }
}
