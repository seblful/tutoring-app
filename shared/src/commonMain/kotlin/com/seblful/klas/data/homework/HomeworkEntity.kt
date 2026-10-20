package com.seblful.klas.data.homework

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.domain.reminders.Reminders
import kotlinx.serialization.Serializable

/**
 * One piece of homework: what was set, for whom, from when, and when it was done.
 *
 * **For one student or for a whole group** — exactly one of [studentId] and [groupId] is set, the pair
 * [RateChangeEntity] uses for the same two owners. A group's is one row, marked done once, rather than a
 * copy per member. `Mappers.kt` reads the pair back as a sealed `Assignee`.
 *
 * **Not tied to a lesson by any column.** The lesson it is checked at is derived: whoever it is for, their
 * next lesson after [givenAtMillis] (`domain/homework/HomeworkRules`). The next lesson is often not booked when
 * homework is set, and can move or be cancelled once it is; a stored link would need a writer at each of
 * those, where a derived one follows them for free.
 *
 * `CASCADE` on both owners: homework is not billed history, so it does not pin a group (deletable while it
 * has no lessons) in place. Students are never deleted.
 *
 * @param doneAtMillis when the tutor marked it done, or null while it is current — done is the whole of
 *   its state, and the archive is the done ones.
 */
@Serializable
@Entity(
    tableName = "homework",
    indices = [Index("studentId"), Index("groupId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class HomeworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long? = null,
    val groupId: Long? = null,
    val text: String,
    val givenAtMillis: Long,
    val doneAtMillis: Long? = null,
)
