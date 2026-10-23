package com.seblful.klas.ui.onboarding

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The first lesson setup was asked to book — for the student it just added — carried across the switch from
 * setup to the app, where the booking form opens for it once. In memory only: a setup the process died in the
 * middle of is not one to resume into a form.
 */
@SingleIn(AppScope::class)
@Inject
class FirstLesson {
    private var studentId: Long? = null

    fun bookFor(studentId: Long) {
        this.studentId = studentId
    }

    /** The student to book for, once: a second ask — a rotation, a return to the app — finds nothing. */
    fun take(): Long? = studentId.also { studentId = null }
}
