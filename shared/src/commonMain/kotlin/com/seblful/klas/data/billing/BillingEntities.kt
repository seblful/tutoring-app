package com.seblful.klas.data.billing

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.PaymentMethod
import kotlinx.serialization.Serializable

/** Current price for one (student, subject). Completed classes snapshot their own charge. */
@Serializable
@Entity(
    tableName = "prices",
    primaryKeys = ["studentId", "subjectId"],
    indices = [Index("subjectId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PriceEntity(
    val studentId: Long,
    val subjectId: Long,
    val amountMinor: Long,
)

/**
 * One recorded change of an hourly rate — a student's price for a subject, or a group's own rate.
 *
 * **Two shapes in one table, on purpose.** A row is either about a (student, subject) price or about
 * a group's rate, so exactly one of `(studentId, subjectId)` / `groupId` is set and the other is
 * null. Splitting it into two tables would double the DAO, the backup entry and the UI for what the
 * tutor reads as one question ("what did this cost before?"), so the discrimination is done once, on
 * the way out of storage: [com.seblful.klas.domain.model.RateOwner] is the sealed domain shape and
 * `Mappers.kt` is the only place these nullable columns are interpreted.
 *
 * `CASCADE` on all three parents, matching [PriceEntity]: this is a log *about* a price, not income.
 * A subject or group that can still be deleted (one with no lessons) takes its rate log with it,
 * rather than leaving rows describing a rate for something that no longer exists. Income is what
 * gets `RESTRICT`, and it lives on [AttendanceEntity].
 */
@Serializable
@Entity(
    tableName = "price_history",
    indices = [Index("studentId"), Index("subjectId"), Index("groupId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RateChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Set together with [subjectId] for an individual price; null for a group's rate. */
    val studentId: Long? = null,
    val subjectId: Long? = null,
    /** Set for a group's own rate; null for an individual price. */
    val groupId: Long? = null,
    /** The rate before the change, or null when this row is where the rate started. */
    val fromMinor: Long? = null,
    val toMinor: Long,
    /**
     * When the change was recorded — **null for a rate that predates the log**. Nothing writes such
     * a row today, but a restored backup can carry one, so the ordering still has to hold. See
     * [com.seblful.klas.domain.model.RateChange.at]: the amount is known, the date is not, and
     * `ORDER BY atMillis DESC` sorts these last, since SQLite treats NULL as the smallest value.
     */
    val atMillis: Long? = null,
)

@Serializable
@Entity(
    tableName = "payments",
    indices = [Index("studentId")],
    // RESTRICT: a payment is part of the financial record; the DB must refuse to delete a student who
    // still has payments rather than cascade them away as silent loss. (Students are archive-only.)
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val dateMillis: Long,
    val amountMinor: Long,
    /** Schema v13 and backup v10; null for every payment recorded before, and whenever it was not said. */
    val method: PaymentMethod? = null,
)

/** One student's summed charges and payments — see [BalanceDao]. */
data class BalanceRow(val studentId: Long, val chargedMinor: Long, val paidMinor: Long)
