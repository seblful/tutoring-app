package com.seblful.repetit.data.backup

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.PaymentEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.local.SubjectEntity
import kotlinx.serialization.Serializable

/**
 * A full snapshot of the database, serialized to JSON for backup/restore. Stores the raw
 * persisted rows verbatim (ids included) so importing into an empty database reproduces the
 * exact same data. [version] guards the format for future migrations.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val subjects: List<SubjectEntity> = emptyList(),
    val students: List<StudentEntity> = emptyList(),
    val studentSubjects: List<StudentSubjectCrossRef> = emptyList(),
    val prices: List<PriceEntity> = emptyList(),
    val classes: List<ClassEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * Whether a backup of [version] can be safely imported by this build. A newer version may
         * carry data this build doesn't understand, so it's rejected rather than imported with
         * fields silently dropped (the parser ignores unknown keys).
         */
        fun isSupported(version: Int): Boolean = version in 1..CURRENT_VERSION
    }
}
