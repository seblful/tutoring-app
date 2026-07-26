package com.seblful.klas.domain.model

/**
 * Where a class is held: in person («Очно») or remotely («Удалённо»).
 *
 * Each [Student] has a [Student.defaultMode]; a new class starts from it but can be changed
 * per occurrence (see [TutoringClass.mode]).
 */
enum class ClassMode {
    IN_PERSON,
    REMOTE,
}
