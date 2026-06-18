package com.seblful.tutoringapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val archived: Boolean = false,
)

/** Which subjects a student takes. */
@Serializable
@Entity(
    tableName = "student_subjects",
    primaryKeys = ["studentId", "subjectId"],
    indices = [Index("subjectId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class StudentSubjectCrossRef(
    val studentId: Long,
    val subjectId: Long,
)

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

@Serializable
@Entity(
    tableName = "classes",
    indices = [Index("studentId"), Index("subjectId"), Index("startMillis"), Index("status")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val status: String,
    val chargeMinor: Long = 0,
    val recurringGroupId: Long? = null,
    val location: String = "",
    val calendarEventId: String? = null,
)

@Serializable
@Entity(
    tableName = "payments",
    indices = [Index("studentId")],
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val dateMillis: Long,
    val amountMinor: Long,
)
