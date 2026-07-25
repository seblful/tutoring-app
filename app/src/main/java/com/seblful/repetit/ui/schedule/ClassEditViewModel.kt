package com.seblful.repetit.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.PriceRepository
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.ClassSession
import com.seblful.repetit.domain.GroupRules
import com.seblful.repetit.domain.Scheduling
import com.seblful.repetit.domain.SessionEdit
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.format.MoneyInput
import com.seblful.repetit.format.toAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Who a lesson is booked for: one student, or a whole group. */
enum class ClassTarget { STUDENT, GROUP }

/**
 * The class-booking form. Date and times start **empty** — the user must choose them (the pickers
 * open on today and on the current time, see [ClassEditViewModel.startPickerDefault]). Duration is
 * expressed as a time range: [timeTo] follows
 * [timeFrom] — keeping the lesson's length (the default lesson when none is set yet) whenever the
 * start changes — and stays independently editable.
 *
 * The form covers both kinds of booking, and differs between them only in *who* the lesson is for:
 * one student, or a whole group. Everything else — subject, date, time, mode and the single
 * [priceText] — belongs to the lesson. That one rate is pre-filled from the student's price for the
 * subject, or from the **group's own rate**, and every class the lesson writes is booked at it.
 */
data class ClassForm(
    val target: ClassTarget = ClassTarget.STUDENT,
    val studentId: Long? = null,
    val groupId: Long? = null,
    val subjectId: Long? = null,
    val date: LocalDate? = null,
    val timeFrom: LocalTime? = null,
    val timeTo: LocalTime? = null,
    val priceText: String = "",
    val mode: ClassMode = ClassMode.IN_PERSON,
    /**
     * Members left out of **this one** group lesson, while booking it. Someone away this week is not
     * off the roster, so the exclusion lives on the form and not on the group: it decides which
     * classes get written now and is forgotten afterwards.
     *
     * Only meaningful for a new lesson. Editing a booked one cannot add or drop a member — those
     * classes are billed history — so the list is read-only there and this stays empty.
     */
    val excludedMemberIds: Set<Long> = emptySet(),
) {
    /** Minutes between [timeFrom] and [timeTo], or null when the range isn't a positive duration. */
    val durationMinutes: Int?
        get() {
            if (timeFrom == null || timeTo == null) return null
            val minutes = Duration.between(timeFrom, timeTo).toMinutes()
            return if (minutes > 0) minutes.toInt() else null
        }

    val price: Money? get() = MoneyInput.rate(priceText)

    private val timingValid: Boolean
        get() = date != null && timeFrom != null && timeTo != null && (durationMinutes ?: 0) > 0

    val isValid: Boolean
        get() = subjectId != null && timingValid && price != null && when (target) {
            ClassTarget.STUDENT -> studentId != null
            ClassTarget.GROUP -> groupId != null
        }

    /**
     * The stretch of time this form describes, or null while the date and times are incomplete. The
     * one place the form's wall-clock values become an instant, so the conflict check and the save
     * can never disagree about when the lesson is.
     */
    fun slot(zone: ZoneId): Scheduling.Slot? {
        val day = date ?: return null
        val from = timeFrom ?: return null
        val minutes = durationMinutes ?: return null
        return Scheduling.Slot(day.atTime(from).atZone(zone).toInstant(), minutes)
    }
}

/**
 * A lesson already in the schedule at the time a form or a move is pointing at. There is one tutor,
 * so this is a refusal rather than a warning: [who] (the group's name, or the student's) and [slot]
 * say which lesson is in the way, so the tutor can pick another time or move that one.
 */
data class TimeConflict(val slot: Scheduling.Slot, val who: String) {
    companion object {
        /**
         * Names the lesson standing in the way: its group when it is a group lesson, otherwise the
         * student whose class it is. Blank when neither row can be found, which leaves the message
         * saying *when* it clashes — better than not refusing at all.
         */
        fun of(clash: ClassSession, groupName: String?, studentName: String?): TimeConflict =
            TimeConflict(Scheduling.slotOf(clash.first), groupName ?: studentName ?: "")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClassEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val classes: ClassRepository,
    private val studentsRepo: StudentRepository,
    private val groupsRepo: GroupRepository,
    private val subjectsRepo: SubjectRepository,
    private val pricesRepo: PriceRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val classId: Long = savedStateHandle["id"] ?: 0L

    /** Non-zero when the form was opened from a group, to book that group straight away. */
    private val preselectedGroupId: Long = savedStateHandle["group"] ?: 0L

    val isNew: Boolean = classId == 0L

    /** Pre-fills the end time when a start is picked; loaded from settings. */
    private var defaultLessonMinutes: Long = 60

    /**
     * The lesson being edited, if any. A group lesson is loaded as the whole session (every member's
     * class), never as the single class the user happened to tap, so saving can't update one member
     * and leave the rest behind. Null while booking something new.
     *
     * A flow rather than a plain field because [conflict] has to react to it: the lesson being edited
     * is the one lesson allowed to occupy the time the form is pointing at.
     */
    private val session = MutableStateFlow<ClassSession?>(null)

    val students: StateFlow<List<Student>> = studentsRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val form = MutableStateFlow(ClassForm())

    /**
     * The groups offered in the picker: the active ones, plus the group of the lesson being edited so
     * an archived group's existing lesson can still name itself.
     */
    val groups: StateFlow<List<Group>> =
        combine(groupsRepo.observeAll(), form.map { it.groupId }.distinctUntilChanged()) { all, selected ->
            all.filter { !it.archived || it.id == selected }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Who the lesson covers, listed under the group so the tutor can see it. While **booking**, the
     * group's bookable roster (archived students left out). While **editing**, the members the lesson
     * was actually booked for — a roster change since then must not add or drop a billed class, so the
     * form shows the lesson as it is, not as the group is now.
     */
    val members: StateFlow<List<Student>> =
        combine(form.map { it.groupId }.distinctUntilChanged(), session) { groupId, booked ->
            MembersKey(groupId, booked?.classes?.map { it.studentId }?.toSet())
        }.distinctUntilChanged()
            .flatMapLatest { key ->
                when {
                    key.groupId == null -> flowOf(emptyList())
                    key.bookedIds != null ->
                        studentsRepo.observeAll().map { all -> all.filter { it.id in key.bookedIds } }
                    else -> membersOf(key.groupId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [bookedIds] is null while booking something new, and the lesson's members while editing one. */
    private data class MembersKey(val groupId: Long?, val bookedIds: Set<Long>?)

    /**
     * The subjects this lesson may be booked for. For a **student**: those with a price set for them,
     * so the class can be billed at completion. For a **group** that has a subject: only that one —
     * the group is taught it, and every member is priced for it. For a group without one (made before
     * v6): all of them, as before, because nothing on the group says otherwise.
     */
    val subjects: StateFlow<List<Subject>> =
        form.map { SubjectKey(it.target, it.studentId, it.groupId) }.distinctUntilChanged()
            .flatMapLatest { key ->
                when {
                    key.target == ClassTarget.STUDENT && key.studentId != null ->
                        combine(
                            pricesRepo.observeForStudent(key.studentId),
                            subjectsRepo.observeAll(),
                        ) { prices, all -> Scheduling.assignableSubjects(all, prices.map { it.subjectId }) }

                    key.target == ClassTarget.GROUP && key.groupId != null ->
                        combine(
                            groupsRepo.observeById(key.groupId),
                            subjectsRepo.observeAll(),
                        ) { group, all ->
                            val bound = group?.subjectId
                            if (bound == null) all else all.filter { it.id == bound }
                        }

                    else -> flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether the subject is decided by the group rather than by this lesson — true once the chosen
     * group has one. Drives the picker's enabled state: showing a dropdown that can only be set to
     * what it already says would invite a choice that isn't there.
     */
    val subjectFixedByGroup: StateFlow<Boolean> =
        form.map { it.groupId }.distinctUntilChanged()
            .flatMapLatest { groupId ->
                if (groupId == null) flowOf(false)
                else groupsRepo.observeById(groupId).map { it?.subjectId != null }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private data class SubjectKey(val target: ClassTarget, val studentId: Long?, val groupId: Long?)

    /**
     * The lesson already booked at the time the form points at, or null when that time is free. Live,
     * so a clash shows up the moment a date or time is picked instead of on save — the tutor is one
     * person and cannot teach two lessons at once.
     *
     * The lesson being **edited** is excluded by every one of its class ids, not just the one that was
     * tapped: a group lesson would otherwise be found colliding with its own other members.
     */
    val conflict: StateFlow<TimeConflict?> =
        combine(
            form.map { it.slot(zone) }.distinctUntilChanged(),
            session.map { s -> s?.classes?.map { it.id }?.toSet().orEmpty() }.distinctUntilChanged(),
        ) { slot, editedIds -> slot to editedIds }
            .flatMapLatest { (slot, editedIds) ->
                if (slot == null) {
                    flowOf(null)
                } else {
                    combine(
                        classes.observeConflicts(slot, editedIds),
                        studentsRepo.observeAll(),
                        groupsRepo.observeAll(),
                    ) { clashing, allStudents, allGroups ->
                        clashing.firstOrNull()?.let { describe(it, allStudents, allGroups) }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Archived students and groups are looked up too — a past lesson still occupied that hour. */
    private fun describe(clash: ClassSession, students: List<Student>, groups: List<Group>): TimeConflict =
        TimeConflict.of(
            clash = clash,
            groupName = clash.groupId?.let { id -> groups.firstOrNull { it.id == id }?.name },
            studentName = students.firstOrNull { it.id == clash.first.studentId }?.name,
        )

    init {
        if (!isNew) {
            viewModelScope.launch { loadExisting() }
        } else if (preselectedGroupId != 0L) {
            setTarget(ClassTarget.GROUP)
            selectGroup(preselectedGroupId)
        }
        viewModelScope.launch { defaultLessonMinutes = settingsRepo.get().defaultLessonMinutes.toLong() }
        // Auto-select the only student there is (a new class, before any choice). With several
        // students, the user picks — we don't guess.
        if (isNew) {
            viewModelScope.launch {
                students.collect { list ->
                    val f = form.value
                    if (f.target == ClassTarget.STUDENT && f.studentId == null && list.size == 1) {
                        selectStudent(list.first().id)
                    }
                }
            }
        }
        // Auto-select the only subject the chosen student/group has (and pre-fill its price). With
        // several subjects, the user picks — we don't guess.
        viewModelScope.launch {
            subjects.collect { list ->
                if (hasTarget() && form.value.subjectId == null && list.size == 1) {
                    selectSubject(list.first().id)
                }
            }
        }
    }

    private suspend fun loadExisting() {
        val loaded = classes.getSessionFor(classId) ?: return
        session.value = loaded
        val c = loaded.first
        val local = c.start.atZone(zone)
        val from = local.toLocalTime()
        form.value = ClassForm(
            target = if (loaded.isGroup) ClassTarget.GROUP else ClassTarget.STUDENT,
            studentId = c.studentId.takeIf { !loaded.isGroup },
            groupId = c.groupId,
            subjectId = c.subjectId,
            date = local.toLocalDate(),
            timeFrom = from,
            timeTo = from.plusMinutes(c.durationMinutes.toLong()),
            // The rate the lesson was booked at. A group lesson from before groups had a rate of their
            // own can have members on differing rates, and no single number is the truth about it —
            // the group's current rate is then offered instead of picking one member's arbitrarily.
            priceText = (loaded.sharedPrice ?: groupRate(c.groupId) ?: c.price).toAmountInput(),
            mode = c.mode,
        )
    }

    private suspend fun groupRate(groupId: Long?): Money? =
        groupId?.let { groupsRepo.getById(it)?.price }

    /** The group's members that a new lesson may be booked for. */
    private fun membersOf(groupId: Long): Flow<List<Student>> =
        combine(groupsRepo.observeMemberIds(groupId), studentsRepo.observeAll()) { ids, all ->
            val memberIds = ids.toSet()
            GroupRules.bookableMembers(all.filter { it.id in memberIds })
        }

    private fun hasTarget(): Boolean = with(form.value) {
        when (target) {
            ClassTarget.STUDENT -> studentId != null
            ClassTarget.GROUP -> groupId != null
        }
    }

    fun update(transform: (ClassForm) -> ClassForm) {
        form.value = transform(form.value)
    }

    /**
     * Switches between booking for a student and for a group. The subject and the price are cleared
     * because they belonged to the other target — a group's rate and a student's price for a subject
     * have nothing to do with each other.
     */
    fun setTarget(target: ClassTarget) {
        if (form.value.target == target) return
        form.value = form.value.copy(
            target = target,
            studentId = null,
            groupId = null,
            subjectId = null,
            priceText = "",
        )
        // Auto-select the only group there is, mirroring the single-student case.
        if (target == ClassTarget.GROUP) {
            val only = groups.value.singleOrNull()
            if (only != null) selectGroup(only.id)
        }
    }

    /**
     * Choosing a (different) student clears the subject and price (they belonged to the previous
     * student) and pre-fills the class mode from the new student's default.
     */
    fun selectStudent(studentId: Long) {
        if (form.value.studentId == studentId) return
        val mode = students.value.firstOrNull { it.id == studentId }?.defaultMode ?: ClassMode.IN_PERSON
        form.value = form.value.copy(studentId = studentId, subjectId = null, priceText = "", mode = mode)
    }

    /**
     * Choosing a (different) group clears the subject, then pre-fills the lesson's rate and mode from
     * the group's own. The rate comes from the *group*, not from the subject, so unlike a student's it
     * is known as soon as the group is — there is nothing left to wait for.
     */
    fun selectGroup(groupId: Long) {
        if (form.value.groupId == groupId) return
        // A different group is a different set of people, so last group's exclusions mean nothing.
        form.value = form.value.copy(
            groupId = groupId,
            subjectId = null,
            priceText = "",
            excludedMemberIds = emptySet(),
        )
        // Read the group from the repository, not from `groups`: the form can be opened already
        // pointed at a group (from that group's screen), before that flow has emitted.
        viewModelScope.launch {
            val group = groupsRepo.getById(groupId) ?: return@launch
            if (form.value.groupId != groupId) return@launch
            form.value = form.value.copy(
                mode = group.defaultMode,
                // Never clobber an amount the tutor has already typed for this lesson.
                priceText = form.value.priceText.ifBlank { group.price.toAmountInput() },
                // A group that has a subject decides it — there is nothing to pick per lesson.
                subjectId = group.subjectId ?: form.value.subjectId,
            )
        }
    }

    /**
     * Includes or excludes one member for the lesson being booked. Excluding writes no class for them
     * at all, which is different from booking and then cancelling: there is no cancelled lesson in
     * their history, because the lesson was never theirs.
     */
    fun setMemberIncluded(studentId: Long, included: Boolean) {
        val excluded = form.value.excludedMemberIds
        form.value = form.value.copy(
            excludedMemberIds = if (included) excluded - studentId else excluded + studentId,
        )
    }

    /**
     * Selecting a subject pre-fills the rate from the student's price for it. A group lesson's rate is
     * the group's and does not depend on the subject, so there is nothing to pre-fill there.
     */
    fun selectSubject(subjectId: Long) {
        form.value = form.value.copy(subjectId = subjectId)
        if (form.value.target == ClassTarget.STUDENT) prefillStudentPrice(subjectId)
    }

    private fun prefillStudentPrice(subjectId: Long) {
        val studentId = form.value.studentId ?: return
        viewModelScope.launch {
            val price = pricesRepo.get(studentId, subjectId)?.amount
            // Only auto-fill an empty field — never clobber a price the user already typed.
            if (price != null && form.value.subjectId == subjectId && form.value.priceText.isBlank()) {
                form.value = form.value.copy(priceText = price.toAmountInput())
            }
        }
    }

    /**
     * Picking or changing the start time moves the end with it so the lesson keeps its length:
     * the current duration if an end was already set, otherwise the default lesson length. This
     * stops the end from going stale (and the duration from breaking) when the start is edited.
     */
    fun setTimeFrom(time: LocalTime) {
        val f = form.value
        val minutes = f.durationMinutes?.toLong() ?: defaultLessonMinutes
        form.value = f.copy(timeFrom = time, timeTo = time.plusMinutes(minutes))
    }

    fun setTimeTo(time: LocalTime) {
        form.value = form.value.copy(timeTo = time)
    }

    /**
     * What the start-time picker opens on: the start already chosen, or — for a lesson whose time is
     * still empty — the current time, since a booking is normally written down as it is arranged.
     * [now] is a parameter so the rule can be tested without depending on the wall clock.
     */
    fun startPickerDefault(now: LocalTime = LocalTime.now(zone)): LocalTime =
        form.value.timeFrom ?: now.truncatedTo(ChronoUnit.MINUTES)

    /**
     * What the end-time picker opens on: the end already chosen, otherwise one default lesson after
     * the start the form would offer — the same length [setTimeFrom] would have applied.
     */
    fun endPickerDefault(now: LocalTime = LocalTime.now(zone)): LocalTime =
        form.value.timeTo ?: startPickerDefault(now).plusMinutes(defaultLessonMinutes)

    /**
     * Saves the form as one lesson. Booking a student and booking a group differ only in **who** the
     * classes are written for — one student, or the group's roster — so both go through the same two
     * repository writes: [ClassRepository.book] for a new lesson, [ClassRepository.apply] for an edit.
     * The rules (re-plan on a move, then rebill) run inside those, so this can't leave a class carrying
     * a stale charge.
     *
     * Does nothing — [onDone] is not called — when the form is incomplete or its time is already
     * taken; [conflict] is what tells the tutor which lesson is in the way.
     */
    fun save(onDone: () -> Unit) {
        val f = form.value
        if (!f.isValid) return
        val slot = f.slot(zone) ?: return
        val start = slot.start
        val duration = slot.durationMinutes
        val isGroup = f.target == ClassTarget.GROUP
        val price = f.price!!

        viewModelScope.launch {
            val existing = session.value
            // Re-checked here against the database, not against [conflict]: the save button is already
            // disabled on a clash, but the lesson in the way may have been booked — or moved here —
            // since this form was opened, and two lessons at one hour cannot both happen.
            val ignoring = existing?.classes?.map { it.id }?.toSet().orEmpty()
            if (classes.conflictsWith(slot, ignoring).isNotEmpty()) return@launch
            if (existing != null) {
                classes.apply(
                    session = existing,
                    changes = SessionEdit(
                        subjectId = f.subjectId!!,
                        durationMinutes = duration,
                        mode = f.mode,
                        price = price,
                        // An individual class may be re-attributed to another student, which credits
                        // the new owner; a group lesson's members come from its roster instead.
                        studentId = if (isGroup) null else f.studentId,
                    ),
                    newStart = start,
                )
            } else {
                val studentIds =
                    if (isGroup) bookableRoster(f.groupId!!) - f.excludedMemberIds
                    else listOf(f.studentId!!)
                // A group whose every member has been archived has nobody left to bill; the lesson
                // would be a row belonging to no one, so there is nothing to book.
                if (studentIds.isEmpty()) return@launch
                classes.book(
                    subjectId = f.subjectId!!,
                    start = start,
                    durationMinutes = duration,
                    mode = f.mode,
                    studentIds = studentIds,
                    price = price,
                    groupId = if (isGroup) f.groupId else null,
                )
            }
            onDone()
        }
    }

    /**
     * The group's members a new lesson is written for. Read straight from the repository rather than
     * from [members], so a save doesn't depend on whether anything is collecting that flow yet.
     */
    private suspend fun bookableRoster(groupId: Long): List<Long> {
        val ids = groupsRepo.getMemberIds(groupId).toSet()
        val active = studentsRepo.observeActive().first().filter { it.id in ids }
        return GroupRules.bookableMembers(active).map { it.id }
    }
}
