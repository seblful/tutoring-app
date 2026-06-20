package com.seblful.repetit.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.notifications.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/** Posts a reminder for one class, if it is still scheduled and hasn't already ended. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val classId = inputData.getLong(KEY_CLASS_ID, -1L)
        if (classId < 0) return Result.failure()

        // Reminders are opt-out in settings (on by default).
        if (!settings.get().notificationsEnabled) return Result.success()

        val klass = classes.getById(classId) ?: return Result.success()
        // The class may have been cancelled, moved away, or completed since we scheduled this.
        if (klass.status != ClassStatus.SCHEDULED) return Result.success()
        if (!klass.end.isAfter(Instant.now())) return Result.success()

        val studentName = students.getById(klass.studentId)?.name.orEmpty()
        val subjectName = subjects.getById(klass.subjectId)?.name.orEmpty()
        notifier.showClassReminder(klass, studentName, subjectName)
        return Result.success()
    }

    companion object {
        const val KEY_CLASS_ID = "classId"
    }
}
