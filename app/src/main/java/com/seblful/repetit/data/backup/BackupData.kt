package com.seblful.repetit.data.backup

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.GroupEntity
import com.seblful.repetit.data.local.GroupMemberCrossRef
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
    val groups: List<GroupEntity> = emptyList(),
    val groupMembers: List<GroupMemberCrossRef> = emptyList(),
    val prices: List<PriceEntity> = emptyList(),
    val classes: List<ClassEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
) {
    companion object {
        /**
         * 2 added groups; **3 gave a group its own hourly rate** (`student_groups.priceMinor`).
         *
         * Older files still import — a v1 has no groups at all, and a v2's groups come back priced at
         * 0 for the tutor to re-price, exactly as the v4 → v5 database migration leaves them. The
         * bump is for the other direction: a v3 file *would* parse in a build that predates the
         * column, which would silently drop every group's rate on the floor, so that build refuses it
         * instead (see [isSupported]).
         */
        const val CURRENT_VERSION = 3

        /**
         * Whether a backup of [version] can be safely imported by this build. A newer version may
         * carry data this build doesn't understand, so it's rejected rather than imported with
         * fields silently dropped (the parser ignores unknown keys).
         */
        fun isSupported(version: Int): Boolean = version in 1..CURRENT_VERSION
    }
}
