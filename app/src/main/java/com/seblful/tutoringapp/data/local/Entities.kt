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
    val phone: String = "",
    val email: String = "",
    val note: String = "",
    val defaultMode: String = "IN_PERSON",
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
    // RESTRICT, not CASCADE: a class is recognized income and history. The schema itself must refuse
    // to delete a student or subject that still has classes, so no future code path can silently
    // erase earned income by removing a parent. Subjects are additionally guarded at the repository
    // (`SubjectRepository.delete`); students have no delete path at all (archive only).
    foreignKeys = [
        ForeignKey(StudentEntity::class, ["id"], ["studentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(SubjectEntity::class, ["id"], ["subjectId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class ClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val startMillis: Long,
    val durationMinutes: Int,
    val priceMinor: Long = 0,
    val status: String,
    val chargeMinor: Long = 0,
    val recurringGroupId: Long? = null,
    val mode: String = "IN_PERSON",
    val calendarEventId: String? = null,
)

/**
 * App-wide settings. A single row (always [SINGLETON_ID]); device-local, so it is intentionally
 * not part of the backup snapshot.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val notificationsEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    /** Minutes before a class that reminders fire (in-app and email). */
    val reminderLeadMinutes: Int = 60,
    /**
     * Whether finished classes auto-complete (and bill) without manual confirmation. **Off by
     * default**: billing a class the tutor hasn't confirmed risks over-stating a student's debt, so
     * the safe default is to have the tutor mark each lesson completed. Opt in via Settings.
     */
    val autoCompleteEnabled: Boolean = false,
    /** "SYSTEM" | "LIGHT" | "DARK". */
    val themeMode: String = "SYSTEM",
    /** Pre-fills the end time when booking a class. */
    val defaultLessonMinutes: Int = 60,
    /** Currency code used to display all money, e.g. "BYN" or "RUB". */
    val currency: String = "BYN",
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

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
)
