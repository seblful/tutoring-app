package com.seblful.klas.domain.lesson

/** Schedule and external actions allow early marks; Today offers started lessons, or only finished ones. */
enum class CompletionTiming {
    ANY_TIME,
    STARTED,
    FINISHED,
    ;

    fun allows(phase: LessonPhase): Boolean = when (this) {
        ANY_TIME -> phase != LessonPhase.SETTLED
        STARTED -> phase == LessonPhase.IN_PROGRESS || phase == LessonPhase.OVERDUE
        FINISHED -> phase == LessonPhase.OVERDUE
    }
}
