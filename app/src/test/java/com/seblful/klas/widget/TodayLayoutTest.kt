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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodayLayoutTest {

    private val texts = TodayTexts("Today", "No lessons today", "Book a lesson", "Mark as taught", "Taught")

    private val openToday = Intent("today")
    private val bookLesson = Intent("book")
    private val anyOther = Intent("other")

    private fun GlanceAppWidgetUnitTest.today(rows: List<TodayRow>, size: DpSize = DpSize(300.dp, 240.dp)) {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(size)
        provideComposable {
            TodayLayout(
                texts,
                rows,
                openToday = actionStartActivity(openToday),
                bookLesson = actionStartActivity(bookLesson),
                openLesson = { actionStartActivity(anyOther) },
                markTaught = { actionStartActivity(anyOther) },
            )
        }
    }

    @Test
    fun `each lesson shows who, then when and in what, under a titled bar`() = runGlanceAppWidgetUnitTest {
        today(
            listOf(
                TodayRow(1, "Аня", "09:00 · Алгебра", taught = true, canMarkTaught = false),
                TodayRow(2, "11 «А»", "16:00 · Алгебра", taught = false, canMarkTaught = true),
            ),
        )

        onNode(hasText("Today")).assertExists()
        onNode(hasText("Аня")).assertExists()
        onNode(hasText("16:00 · Алгебра")).assertExists()
        onNode(hasContentDescriptionEqualTo("Taught")).assertExists()
        onNode(hasContentDescriptionEqualTo("Mark as taught")).assertExists()
    }

    @Test
    fun `the title bar opens the day, and its plus books`() = runGlanceAppWidgetUnitTest {
        today(listOf(TodayRow(1, "Аня", "09:00", taught = false, canMarkTaught = true)))

        onNode(hasStartActivityClickAction(openToday)).assertExists()
        onNode(hasStartActivityClickAction(bookLesson)).assertExists()
        onNode(hasContentDescriptionEqualTo("Book a lesson")).assertExists()
    }

    @Test
    fun `a free day says so and offers to book`() = runGlanceAppWidgetUnitTest {
        today(emptyList())

        onNode(hasText("No lessons today")).assertExists()
        onNode(hasText("Book a lesson")).assertExists()
    }

    @Test
    fun `a narrow free day still offers to book`() = runGlanceAppWidgetUnitTest {
        today(emptyList(), DpSize(172.dp, 234.dp))

        onNode(hasText("Book a lesson")).assertExists()
    }

    @Test
    fun `a widget too short for a title bar gives the lessons all of it`() = runGlanceAppWidgetUnitTest {
        today(listOf(TodayRow(1, "Аня", "09:00", taught = false, canMarkTaught = true)), DpSize(300.dp, 115.dp))

        onNode(hasText("Today")).assertDoesNotExist()
        onNode(hasStartActivityClickAction(openToday)).assertDoesNotExist()
        onNode(hasText("Аня")).assertExists()
    }
}
