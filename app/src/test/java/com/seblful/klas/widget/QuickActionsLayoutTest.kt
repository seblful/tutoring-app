package com.seblful.klas.widget

import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickActionsLayoutTest {

    private val openApp = Intent("app")
    private val book = Intent("book")

    private fun GlanceAppWidgetUnitTest.shortcuts(size: DpSize) {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(size)
        val other = actionStartActivity(Intent("other"))
        provideComposable {
            QuickActionsLayout(
                appName = "Klas",
                openApp = actionStartActivity(openApp),
                headerButton = ToolBarButton(R.drawable.widget_icon_add, "Book a lesson", actionStartActivity(book)),
                buttons = listOf(
                    ToolBarButton(R.drawable.widget_icon_today, "Today", other),
                    ToolBarButton(R.drawable.widget_icon_add_student, "Add a student", other),
                    ToolBarButton(R.drawable.widget_icon_homework, "Homework", other),
                    ToolBarButton(R.drawable.widget_icon_debts, "Debts", other),
                ),
            )
        }
    }

    @Test
    fun `a one-cell widget is the booking shortcut, not a mark`() = runGlanceAppWidgetUnitTest {
        shortcuts(DpSize(56.dp, 56.dp))

        onNode(hasContentDescriptionEqualTo("Book a lesson").and(hasStartActivityClickAction(book))).assertExists()
        onNode(hasContentDescriptionEqualTo("Klas")).assertDoesNotExist()
    }

    @Test
    fun `the mark opens the app`() = runGlanceAppWidgetUnitTest {
        shortcuts(DpSize(296.dp, 72.dp))

        onNode(hasContentDescriptionEqualTo("Klas").and(hasStartActivityClickAction(openApp))).assertExists()
    }

    @Test
    fun `a row of icons leaves their names to the content descriptions`() = runGlanceAppWidgetUnitTest {
        shortcuts(DpSize(296.dp, 72.dp))

        onNode(hasContentDescriptionEqualTo("Today")).assertExists()
        onNode(hasText("Today")).assertDoesNotExist()
    }

    @Test
    fun `tiles tall enough name their shortcut`() = runGlanceAppWidgetUnitTest {
        shortcuts(DpSize(240.dp, 228.dp))

        onNode(hasText("Klas")).assertExists()
        listOf("Today", "Add a student", "Homework", "Debts").forEach { onNode(hasText(it)).assertExists() }
    }
}
