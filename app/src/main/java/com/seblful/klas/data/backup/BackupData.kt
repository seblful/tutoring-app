package com.seblful.klas.data.backup

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
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
    /** The rate log (`price_history`) — the record of what prices used to be. */
    val rateHistory: List<RateChangeEntity> = emptyList(),
    val classes: List<ClassEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
) {
    companion object {
        /**
         * 2 added groups; **3 gave a group its own hourly rate** (`student_groups.priceMinor`);
         * **4 gave a group its subject** (`student_groups.subjectId`); **5 carries the rate log**
         * (`price_history`).
         *
         * Older files still import — a v1 has no groups at all, a v2's groups come back priced at 0
         * for the tutor to re-price, a v3's come back with no subject, and a v4's arrive with no rate
         * history (an empty log, which is exactly what a database that predates the table has; the
         * v6 → v7 migration's seeding is an *upgrade* story, not an import one, and a restore
         * deliberately reproduces the file rather than inventing rows it doesn't contain). All of that
         * matches what the corresponding database migrations leave behind. The bump is for the other
         * direction: a v5 file *would* parse in a build that predates the table, which would silently
         * drop the whole log on the floor, so that build refuses it instead (see [isSupported]).
         */
        const val CURRENT_VERSION = 5

        /**
         * Whether a backup of [version] can be safely imported by this build. A newer version may
         * carry data this build doesn't understand, so it's rejected rather than imported with
         * fields silently dropped (the parser ignores unknown keys).
         */
        fun isSupported(version: Int): Boolean = version in 1..CURRENT_VERSION
    }
}
