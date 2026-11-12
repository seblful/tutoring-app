package com.seblful.klas.ui.navigation

import androidx.savedstate.read
import androidx.savedstate.serialization.encodeToSavedState
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.ui.groups.GroupSection
import com.seblful.klas.ui.students.StudentSection
import com.seblful.klas.ui.students.StudentTask
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RouteSavingTest : UiTest() {

    private val everyRoute: List<Route> = listOf(
        Routes.Today, Routes.Schedule, Routes.People, Routes.Homework, Routes.Reports, Routes.Subjects,
        Routes.Backup, Routes.Settings, Routes.LessonSettings, Routes.NotificationSettings,
        Routes.CalendarSettings, Routes.AppearanceSettings, Routes.Diagnostics, Routes.About, Routes.StudentAdd,
        Routes.StudentAddForSubject(4), Routes.StudentDetail(1, StudentTask.PAYMENT),
        Routes.StudentList(1, StudentSection.HOMEWORK), Routes.GroupAdd,
        Routes.GroupDetail(2, setHomework = true), Routes.GroupList(2, GroupSection.LESSONS), Routes.GroupEdit(2),
        Routes.classEdit(3, groupId = 2, day = LocalDate(2026, 10, 21), studentId = 1),
    )

    @Test
    fun `every route survives the back stack's trip through saved state`() {
        val stack = everyRoute.map(::Screen)
        val serializer = ListSerializer(Screen.serializer())

        assertEquals(stack, Json.decodeFromString(serializer, Json.encodeToString(serializer, stack)))
    }

    @Test
    fun `a lesson form's arguments reach its view model under the names it reads`() {
        val args = encodeToSavedState(Routes.classEdit(3, groupId = 2, day = LocalDate(2026, 10, 21), studentId = 1))

        args.read {
            assertEquals(3L, getLong("id"))
            assertEquals(2L, getLong("group"))
            assertEquals(1L, getLong("student"))
            assertEquals("2026-10-21", getString("day"))
        }
    }

    @Test
    fun `a new group's form is given no id`() {
        encodeToSavedState(Routes.GroupAdd).read { assertFalse(contains("id")) }
    }

    @Test
    fun `a card's id reaches its view model`() {
        encodeToSavedState(Routes.StudentDetail(7)).read { assertEquals(7L, getLong("id")) }
        encodeToSavedState(Routes.GroupDetail(8)).read { assertEquals(8L, getLong("id")) }
    }
}
