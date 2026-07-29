package com.seblful.klas.testutil

import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.PaymentDao
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceDao
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.RateHistoryDao
import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SettingsEntity
import com.seblful.klas.data.local.SnapshotDao
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.model.ClassStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake DAOs backing the real repositories in JVM unit tests. Each `observe*` is derived
 * from a [MutableStateFlow] so collectors re-emit on every write, mirroring Room's reactive queries.
 * Auto-generated ids start at 1 (like SQLite). These are deliberately simple — they reproduce the
 * query semantics the view models rely on (filtering, time ordering), not Room's full behaviour.
 *
 * They deliberately do **not** order by name. That order belongs to `format/NameOrder` and is applied
 * by the repositories, so these fakes have no version of it to disagree with — which is exactly how
 * they used to: sorting here with Kotlin's `lowercase()` while the real query used SQLite's
 * ASCII-only `COLLATE NOCASE` meant Cyrillic names came out in a different order in tests than in
 * the app.
 */

class FakeSubjectDao : SubjectDao {
    val items = MutableStateFlow<List<SubjectEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<SubjectEntity>> = items
    override suspend fun getById(id: Long): SubjectEntity? = items.value.firstOrNull { it.id == id }

    override suspend fun upsert(subject: SubjectEntity): Long {
        val id = if (subject.id == 0L) nextId++ else subject.id
        val existed = items.value.any { it.id == id }
        items.value = items.value.filterNot { it.id == id } + subject.copy(id = id)
        // Room's @Upsert only returns a rowid when it inserts; an update returns -1. Returning the
        // real id here kept `upsertedId`'s fallback off every JVM path — the branch production takes
        // on every edit was exercised nowhere.
        return if (existed) -1L else id
    }

    override suspend fun delete(subject: SubjectEntity) {
        items.value = items.value.filterNot { it.id == subject.id }
    }
}

class FakeStudentDao : StudentDao {
    val items = MutableStateFlow<List<StudentEntity>>(emptyList())
    val links = MutableStateFlow<List<StudentSubjectCrossRef>>(emptyList())
    private var nextId = 1L

    override fun observeActive(): Flow<List<StudentEntity>> =
        items.map { list -> list.filterNot { it.archived } }

    override fun observeAll(): Flow<List<StudentEntity>> = items

    override fun observeById(id: Long): Flow<StudentEntity?> =
        items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): StudentEntity? = items.value.firstOrNull { it.id == id }

    override suspend fun upsert(student: StudentEntity): Long {
        val id = if (student.id == 0L) nextId++ else student.id
        val existed = items.value.any { it.id == id }
        items.value = items.value.filterNot { it.id == id } + student.copy(id = id)
        // -1 on update, like Room — see FakeSubjectDao.upsert.
        return if (existed) -1L else id
    }

    override suspend fun getSubjectIds(studentId: Long): List<Long> =
        links.value.filter { it.studentId == studentId }.map { it.subjectId }

    override fun observeStudentIdsForSubject(subjectId: Long): Flow<List<Long>> =
        links.map { list -> list.filter { it.subjectId == subjectId }.map { it.studentId } }

    override suspend fun getStudentIdsForSubject(subjectId: Long): List<Long> =
        links.value.filter { it.subjectId == subjectId }.map { it.studentId }

    override suspend fun addSubjectLink(ref: StudentSubjectCrossRef) {
        // OnConflictStrategy.IGNORE: a duplicate (studentId, subjectId) is a no-op.
        if (links.value.none { it.studentId == ref.studentId && it.subjectId == ref.subjectId }) {
            links.value = links.value + ref
        }
    }

    override suspend fun removeSubjectLink(studentId: Long, subjectId: Long) {
        links.value = links.value.filterNot { it.studentId == studentId && it.subjectId == subjectId }
    }
}

class FakeGroupDao : GroupDao {
    val items = MutableStateFlow<List<GroupEntity>>(emptyList())
    val members = MutableStateFlow<List<GroupMemberCrossRef>>(emptyList())
    private var nextId = 1L

    override fun observeActive(): Flow<List<GroupEntity>> =
        items.map { list -> list.filterNot { it.archived } }

    override fun observeAll(): Flow<List<GroupEntity>> = items

    override suspend fun getAll(): List<GroupEntity> = items.value

    override fun observeById(id: Long): Flow<GroupEntity?> =
        items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): GroupEntity? = items.value.firstOrNull { it.id == id }

    override suspend fun countForSubject(subjectId: Long): Int =
        items.value.count { it.subjectId == subjectId }

    override suspend fun upsert(group: GroupEntity): Long {
        val id = if (group.id == 0L) nextId++ else group.id
        val existed = items.value.any { it.id == id }
        items.value = items.value.filterNot { it.id == id } + group.copy(id = id)
        // Room's @Upsert only returns a rowid when it inserts; an update returns -1. Returning the
        // real id here used to hide a crash that only the device saw. `existed`, not `id != 0`:
        // seeding a fresh row under an explicit id is still an insert, and Room answers its rowid.
        return if (existed) -1L else id
    }

    override suspend fun delete(group: GroupEntity) {
        items.value = items.value.filterNot { it.id == group.id }
        // Mirrors ON DELETE CASCADE on group_members.
        members.value = members.value.filterNot { it.groupId == group.id }
    }

    override fun observeMemberIds(groupId: Long): Flow<List<Long>> =
        members.map { list -> list.filter { it.groupId == groupId }.map { it.studentId } }

    override suspend fun getMemberIds(groupId: Long): List<Long> =
        members.value.filter { it.groupId == groupId }.map { it.studentId }

    override fun observeAllMembers(): Flow<List<GroupMemberCrossRef>> = members

    override suspend fun addMember(ref: GroupMemberCrossRef) {
        // Mirrors the group_members -> student_groups foreign key. Without this the fake happily
        // stored members against a group that does not exist, which is precisely the bug the device
        // hit as a SQLiteConstraintException.
        check(items.value.any { it.id == ref.groupId }) {
            "FOREIGN KEY constraint failed: no group with id ${ref.groupId}"
        }
        // OnConflictStrategy.IGNORE: a duplicate (groupId, studentId) is a no-op.
        if (members.value.none { it.groupId == ref.groupId && it.studentId == ref.studentId }) {
            members.value = members.value + ref
        }
    }

    override suspend fun removeMember(groupId: Long, studentId: Long) {
        members.value = members.value.filterNot { it.groupId == groupId && it.studentId == studentId }
    }
}

class FakePriceDao : PriceDao {
    val items = MutableStateFlow<List<PriceEntity>>(emptyList())

    override fun observeForStudent(studentId: Long): Flow<List<PriceEntity>> =
        items.map { list -> list.filter { it.studentId == studentId } }

    override suspend fun get(studentId: Long, subjectId: Long): PriceEntity? =
        items.value.firstOrNull { it.studentId == studentId && it.subjectId == subjectId }

    override suspend fun upsert(price: PriceEntity) {
        items.value = items.value.filterNot {
            it.studentId == price.studentId && it.subjectId == price.subjectId
        } + price
    }

    override suspend fun delete(studentId: Long, subjectId: Long) {
        items.value = items.value.filterNot { it.studentId == studentId && it.subjectId == subjectId }
    }
}

/**
 * The rate log. Reproduces the DAO's **ordering** (newest first, with the undated "predates the log"
 * rows last) because that is what the UI reads rows in — and, unlike name ordering, it is a rule SQL
 * genuinely owns: `atMillis DESC` puts NULLs last in SQLite, since NULL sorts as the smallest value.
 */
class FakeRateHistoryDao : RateHistoryDao {
    val items = MutableStateFlow<List<RateChangeEntity>>(emptyList())
    private var nextId = 1L

    /** `ORDER BY atMillis DESC, id DESC` — Kotlin's `compareValues` treats null as smallest, as SQLite does. */
    private fun List<RateChangeEntity>.newestFirst(): List<RateChangeEntity> =
        sortedWith(compareByDescending<RateChangeEntity> { it.atMillis }.thenByDescending { it.id })

    override fun observeForStudent(studentId: Long): Flow<List<RateChangeEntity>> =
        items.map { list -> list.filter { it.studentId == studentId }.newestFirst() }

    override fun observeForStudentSubject(studentId: Long, subjectId: Long): Flow<List<RateChangeEntity>> =
        items.map { list ->
            list.filter { it.studentId == studentId && it.subjectId == subjectId }.newestFirst()
        }

    override fun observeForGroup(groupId: Long): Flow<List<RateChangeEntity>> =
        items.map { list -> list.filter { it.groupId == groupId }.newestFirst() }

    override suspend fun insert(change: RateChangeEntity): Long {
        val id = if (change.id == 0L) nextId++ else change.id
        items.value = items.value + change.copy(id = id)
        return id
    }

    override suspend fun deleteForStudentSubject(studentId: Long, subjectId: Long) {
        items.value = items.value.filterNot { it.studentId == studentId && it.subjectId == subjectId }
    }
}

class FakeClassDao : ClassDao {
    val items = MutableStateFlow<List<ClassEntity>>(emptyList())
    private var nextId = 1L

    /**
     * When a class ends — from the domain model, not restated.
     *
     * This was a third copy of the arithmetic, beside `TutoringClass.end` and the SQL in
     * `ClassDao.dueForCompletion`. A double that computes the answer its own way can agree with the
     * production rule today and disagree after one edit, and the disagreement surfaces as a test that
     * passes about behaviour the app does not have.
     */
    private fun endMillis(c: ClassEntity) = c.toDomain().end.toEpochMilli()

    override fun observeById(id: Long): Flow<ClassEntity?> =
        items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): ClassEntity? = items.value.firstOrNull { it.id == id }

    override fun observeAll(): Flow<List<ClassEntity>> = items.map { it.sortedBy { c -> c.startMillis } }

    override fun observeForStudent(studentId: Long): Flow<List<ClassEntity>> =
        items.map { list -> list.filter { it.studentId == studentId }.sortedByDescending { it.startMillis } }

    override fun observeForGroup(groupId: Long): Flow<List<ClassEntity>> =
        items.map { list -> list.filter { it.groupId == groupId }.sortedByDescending { it.startMillis } }

    override fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<ClassEntity>> =
        items.map { list ->
            list.filter { it.startMillis in fromMillis until toMillis }.sortedBy { it.startMillis }
        }

    override suspend fun getBetween(fromMillis: Long, toMillis: Long): List<ClassEntity> =
        items.value.filter { it.startMillis in fromMillis until toMillis }.sortedBy { it.startMillis }

    override suspend fun withCalendarEvent(): List<ClassEntity> =
        items.value.filter { it.calendarEventId != null }

    override suspend fun countForSubject(subjectId: Long): Int =
        items.value.count { it.subjectId == subjectId }

    override suspend fun scheduledForStudent(studentId: Long): List<ClassEntity> =
        items.value.filter { it.studentId == studentId && it.status == ClassStatus.SCHEDULED.name }

    override suspend fun countForStudentSubject(studentId: Long, subjectId: Long): Int =
        items.value.count { it.studentId == studentId && it.subjectId == subjectId }

    override suspend fun countForGroup(groupId: Long): Int =
        items.value.count { it.groupId == groupId }

    override suspend fun getSession(groupId: Long, startMillis: Long): List<ClassEntity> =
        items.value.filter { it.groupId == groupId && it.startMillis == startMillis }.sortedBy { it.id }

    override suspend fun dueForCompletion(nowMillis: Long): List<ClassEntity> =
        items.value.filter { it.status == ClassStatus.SCHEDULED.name && endMillis(it) <= nowMillis }

    override suspend fun nextScheduledAfter(nowMillis: Long): ClassEntity? =
        items.value.filter { it.status == ClassStatus.SCHEDULED.name && it.startMillis > nowMillis }
            .minByOrNull { it.startMillis }

    override suspend fun getAll(): List<ClassEntity> = items.value

    override suspend fun upsert(klass: ClassEntity): Long {
        val id = if (klass.id == 0L) nextId++ else klass.id
        val row = klass.copy(id = id)
        items.value = items.value.filterNot { it.id == id } + row
        return id
    }

    override suspend fun update(klass: ClassEntity) {
        items.value = items.value.map { if (it.id == klass.id) klass else it }
    }

    override suspend fun delete(klass: ClassEntity) {
        items.value = items.value.filterNot { it.id == klass.id }
    }
}

class FakeSettingsDao : SettingsDao {
    val row = MutableStateFlow<SettingsEntity?>(null)

    override fun observe(): Flow<SettingsEntity?> = row
    override suspend fun get(): SettingsEntity? = row.value
    override suspend fun upsert(settings: SettingsEntity) {
        row.value = settings
    }
}

class FakePaymentDao : PaymentDao {
    val items = MutableStateFlow<List<PaymentEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PaymentEntity>> =
        items.map { it.sortedByDescending { p -> p.dateMillis } }

    override fun observeForStudent(studentId: Long): Flow<List<PaymentEntity>> =
        items.map { list -> list.filter { it.studentId == studentId }.sortedByDescending { it.dateMillis } }

    override suspend fun insert(payment: PaymentEntity): Long {
        val id = if (payment.id == 0L) nextId++ else payment.id
        items.value = items.value.filterNot { it.id == id } + payment.copy(id = id)
        return id
    }

    override suspend fun update(payment: PaymentEntity) {
        // Room's @Update matches on the primary key and does nothing when no row has it, so a fake
        // that inserted the missing row would let a test pass against behaviour the real DAO
        // does not have.
        items.value = items.value.map { if (it.id == payment.id) payment else it }
    }

    override suspend fun delete(payment: PaymentEntity) {
        items.value = items.value.filterNot { it.id == payment.id }
    }
}

/**
 * The whole-table seam over the other fakes, so backup and restore are exercised against the same
 * in-memory rows every repository sees.
 *
 * It subclasses the **real** [SnapshotDao] and fills in only its per-table methods, which means the
 * two orders `replace` applies — children before parents out, parents before children in — are the
 * production ones and not a copy. That matters more here than anywhere else: those orders exist to
 * satisfy foreign keys, [FakeGroupDao] is the only fake that enforces one, and a restore whose delete
 * order was wrong would fail on a device while passing here. The fake cannot get it wrong because it
 * is never asked.
 */
class FakeSnapshotDao(
    private val subjectDao: FakeSubjectDao,
    private val studentDao: FakeStudentDao,
    private val groupDao: FakeGroupDao,
    private val priceDao: FakePriceDao,
    private val rateHistoryDao: FakeRateHistoryDao,
    private val classDao: FakeClassDao,
    private val paymentDao: FakePaymentDao,
) : SnapshotDao() {

    override suspend fun subjects(): List<SubjectEntity> = subjectDao.items.value
    override suspend fun students(): List<StudentEntity> = studentDao.items.value
    override suspend fun studentSubjects(): List<StudentSubjectCrossRef> = studentDao.links.value
    override suspend fun groups(): List<GroupEntity> = groupDao.items.value
    override suspend fun groupMembers(): List<GroupMemberCrossRef> = groupDao.members.value
    override suspend fun prices(): List<PriceEntity> = priceDao.items.value
    override suspend fun rateHistory(): List<RateChangeEntity> = rateHistoryDao.items.value
    override suspend fun classes(): List<ClassEntity> = classDao.items.value
    override suspend fun payments(): List<PaymentEntity> = paymentDao.items.value

    override suspend fun insertSubjects(rows: List<SubjectEntity>) {
        subjectDao.items.value += rows
    }

    override suspend fun insertStudents(rows: List<StudentEntity>) {
        studentDao.items.value += rows
    }

    override suspend fun insertStudentSubjects(rows: List<StudentSubjectCrossRef>) {
        studentDao.links.value += rows
    }

    override suspend fun insertGroups(rows: List<GroupEntity>) {
        groupDao.items.value += rows
    }

    override suspend fun insertGroupMembers(rows: List<GroupMemberCrossRef>) {
        groupDao.members.value += rows
    }

    override suspend fun insertPrices(rows: List<PriceEntity>) {
        priceDao.items.value += rows
    }

    override suspend fun insertRateHistory(rows: List<RateChangeEntity>) {
        rateHistoryDao.items.value += rows
    }

    override suspend fun insertClasses(rows: List<ClassEntity>) {
        classDao.items.value += rows
    }

    override suspend fun insertPayments(rows: List<PaymentEntity>) {
        paymentDao.items.value += rows
    }

    override suspend fun clearSubjects() {
        subjectDao.items.value = emptyList()
    }

    override suspend fun clearStudents() {
        studentDao.items.value = emptyList()
    }

    override suspend fun clearStudentSubjects() {
        studentDao.links.value = emptyList()
    }

    override suspend fun clearGroups() {
        groupDao.items.value = emptyList()
    }

    override suspend fun clearGroupMembers() {
        groupDao.members.value = emptyList()
    }

    override suspend fun clearPrices() {
        priceDao.items.value = emptyList()
    }

    override suspend fun clearRateHistory() {
        rateHistoryDao.items.value = emptyList()
    }

    override suspend fun clearClasses() {
        classDao.items.value = emptyList()
    }

    override suspend fun clearPayments() {
        paymentDao.items.value = emptyList()
    }
}
