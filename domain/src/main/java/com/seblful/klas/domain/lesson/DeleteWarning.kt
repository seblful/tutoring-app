package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.TutoringClass

/**
 * Which of the four warnings deleting a lesson owes, and how many classes go with it.
 *
 * Two facts decide it, and both are about the lesson rather than about the screen: whether any of its
 * classes has **billed**, because deleting a completed lesson takes recognized income with it, and
 * whether it is a group's, because then it is several students' classes at once.
 *
 * **[kind] is the decision; the four strings are the screen's.** This type used to publish the two facts
 * instead, and the four-armed `when` combining them stayed in the composable — so what had moved out was
 * the two extractions, not the *picking*, which is the part that can be got wrong and the part no test
 * could reach. Named cases make the screen's `when` exhaustive over an answer somebody else gave, which
 * is the shape `SaveIntent` and `SubjectNotice` already have.
 */
data class DeleteWarning(val kind: Kind, val classes: Int) {

    /** What deleting this lesson costs, in the four combinations that read differently. */
    enum class Kind {
        /** One student's class, nothing billed: the ordinary case. */
        PLAIN,

        /** One student's class that has billed — deleting it removes recognized income. */
        BILLED,

        /** A group's lesson, nothing billed: several students' classes at once. */
        GROUP,

        /** A group's lesson that has billed: both at once, and the only case naming both. */
        BILLED_GROUP,
    }

    companion object {
        /**
         * Billed is the billing rule's ([TutoringClass.isBillable]), not "completed": a lesson taught to
         * nobody who was there — every member marked absent — earned nothing, and deleting it takes no
         * income with it.
         */
        fun of(session: ClassSession): DeleteWarning {
            val billed = session.classes.any { it.isBillable }
            return DeleteWarning(
                kind = when {
                    billed && session.isGroup -> Kind.BILLED_GROUP
                    billed -> Kind.BILLED
                    session.isGroup -> Kind.GROUP
                    else -> Kind.PLAIN
                },
                classes = session.size,
            )
        }
    }
}
