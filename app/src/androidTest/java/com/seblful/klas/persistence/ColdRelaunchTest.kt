package com.seblful.klas.persistence

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.KlasStore
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.di.AppGraph
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Instant

/** Run by scripts/android-relaunch.mjs: the host kills the app between instrumentation invocations. */
@RunWith(AndroidJUnit4::class)
class ColdRelaunchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store by lazy { (context.applicationContext as AppGraph.Holder).graph as KlasStore }
    private val ids by lazy { context.getSharedPreferences("cold-relaunch-test", Context.MODE_PRIVATE) }

    @Test
    fun persistedWorkflow() = runTest {
        val phase = InstrumentationRegistry.getArguments().getString("relaunchPhase")
        assumeNotNull(phase)
        when (phase) {
            "seed" -> seed()
            "verify" -> verify()
            "cleanup" -> cleanup()
            else -> error("Unknown relaunch phase: $phase")
        }
    }

    private suspend fun seed() {
        cleanup()
        val token = UUID.randomUUID().toString()
        val subject = store.subjects.upsert(Subject(name = "Relaunch $token"))
        assertTrue(ids.edit().putLong("subject", subject).putString("subjectName", "Relaunch $token").commit())
        val student = store.enrollment.createStudent(
            Student(name = "Persistence $token"), mapOf(subject to Money(3000)),
        )
        assertTrue(ids.edit().putLong("student", student).putString("studentName", "Persistence $token").commit())
        val id = store.classes.book(
            subject, START, 90, ClassMode.REMOTE, listOf(student), Money(3000),
        ).written().single()
        store.classes.setStatus(store.classes.getSessionFor(id)!!, LessonStatus.COMPLETED).written()
        store.payments.restore(Payment(studentId = student, date = START, amount = Money(1000)))
        assertTrue(ids.edit().putLong("lesson", id).commit())
    }

    private suspend fun verify() {
        val student = ids.getLong("student", 0)
        assertTrue("The seed must come from the earlier process", student > 0)
        val details = store.studentDetails.observe(student).first()
        assertEquals(ids.getString("studentName", null), details.student?.name)
        assertEquals(Balance.Owed(Money(3500)), details.balance)
        assertEquals(Money(1000), details.payments.single().amount)
        val lesson = details.classes.single().klass
        assertEquals(ids.getLong("lesson", 0), lesson.id)
        assertEquals(START, lesson.start)
        assertEquals(LessonStatus.COMPLETED, lesson.status)
        assertEquals(Money(4500), lesson.charge)
        assertEquals(Money(3000), details.subjects.single().price)
    }

    private fun cleanup() {
        if (!ids.contains("subject") && !ids.contains("student")) return
        // This phase never opens the Room graph. Delete this probe's named rows in FK order on
        // one connection, so Room's invalidation work cannot compete with a second writer.
        val deletes = listOf(
            "DELETE FROM lessons WHERE subjectId IN (SELECT id FROM subjects WHERE id = ? AND name = ?)" to "subject",
            "DELETE FROM payments WHERE studentId IN (SELECT id FROM students WHERE id = ? AND name = ?)" to "student",
            "DELETE FROM students WHERE id = ? AND name = ?" to "student",
            "DELETE FROM subjects WHERE id = ? AND name = ?" to "subject",
        )
        BundledSQLiteDriver().open(context.getDatabasePath(AppDatabase.NAME).absolutePath).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA busy_timeout = 5000")
            db.execSQL("BEGIN IMMEDIATE TRANSACTION")
            for ((sql, key) in deletes) {
                val name = ids.getString("${key}Name", null) ?: continue
                db.prepare(sql).use { statement ->
                    statement.bindLong(1, ids.getLong(key, 0))
                    statement.bindText(2, name)
                    statement.step()
                }
            }
            db.execSQL("COMMIT")
        }
        assertTrue(ids.edit().clear().commit())
    }

    private companion object {
        val START = Instant.parse("2000-01-01T10:00:00Z")
    }
}
