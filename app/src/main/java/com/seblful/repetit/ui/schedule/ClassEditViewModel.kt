package com.seblful.repetit.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.EnrollmentRepository
import com.seblful.repetit.data.repository.GroupRepository
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
import com.seblful.repetit.domain.model.TutoringClass
import com.seblful.repetit.format.MoneyField
import com.seblful.repetit.ui.common.UiEvents
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
    val price: MoneyField = MoneyField(kind = MoneyField.Kind.RATE),
    val mode: ClassMode = ClassMode.IN_PERSON,
    /**
     * Members left out of **this one** group lesson. Someone away this week is not off the roster, so
     * the exclusion lives on the form and not on the group: it decides which classes the lesson has
     * and is forgotten afterwards.
     *
     * It means the same thing whether the lesson is being booked or edited, and for a lesson in any
     * status: who was in a lesson is a fact, and a fact can be corrected once the lesson is over.
     * Unticking a member of a saved lesson therefore removes their class ([GroupRules.rosterChange]),
     * which for a completed one takes what they were charged with it.
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

    private val timingValid: Boolean
        get() = date != null && timeFrom != null && timeTo != null && (durationMinutes ?: 0) > 0

    val isValid: Boolean
        get() = subjectId != null && timingValid && price.isValid && when (target) {
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

/**
 * Why the chosen time won't do. Which problem wins is decided here, not by the order two `if`s
 * happen to sit in a composable: an impossible range is the tutor's own typing and is worth saying
 * first, while a clash is about somebody else's lesson.
 */
sealed interface TimeProblem {

    /**
     * The end is at or before the start. A lesson that would cross midnight lands here too — the
     * times are wall-clock on one date, so 23:00 → 00:30 reads as a negative range.
     */
    data object RangeInvalid : TimeProblem

    /** Another lesson already holds this slot; [conflict] names it. */
    data class Taken(val conflict: TimeConflict) : TimeProblem
}

/** What, if anything, to say under the subject picker. */
enum class SubjectNotice {
    /** The group is taught one subject, so there is nothing to pick per lesson. */
    FIXED_BY_GROUP,

    /** The chosen group offers no bookable subject. */
    NONE_FOR_GROUP,

    /** The chosen student has no priced subject yet, so nothing can be billed. */
    NONE_FOR_STUDENT,
}

/**
 * Everything the booking screen draws, with every decision already made.
 *
 * The screen used to receive seven separate flows and work out the answers itself — which time
 * error wins, whether the subject picker is live, whether everyone has been unticked, whether the
 * save may proceed. Those are rules about booking a lesson, not about drawing one, and none of them
 * could be tested where they were. Here they are ordinary properties over ordinary data.
 */
data class ClassFormState(
    val form: ClassForm = ClassForm(),
    val isNew: Boolean = true,
    val students: List<Student> = emptyList(),
    val groups: List<Group> = emptyList(),
    val members: List<Student> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val subjectFixedByGroup: Boolean = false,
    /**
     * The classes the lesson already has — empty while booking. Carried so the form can say what
     * unticking somebody would cost (see [billedDrops]) rather than only what it would do.
     */
    val booked: List<TutoringClass> = emptyList(),
    val conflict: TimeConflict? = null,
    /**
     * Whether the tutor has changed anything — what leaving without saving would throw away.
     *
     * This form tracks having been *touched* rather than differing from a snapshot, unlike the group
     * form: it fills itself in as it goes (the only student, the only subject, the group's rate, the
     * end time following the start), each arriving asynchronously after the screen is up, so there is
     * no one moment when "as it was opened" is settled. The cost is that typing something and undoing
     * it still counts as a change; back then asks a question whose answer is harmless either way.
     */
    val isDirty: Boolean = false,
) {
    /** Whether the lesson yet has somebody to be for. Until then most of the form means nothing. */
    val targetChosen: Boolean
        get() = when (form.target) {
            ClassTarget.STUDENT -> form.studentId != null
            ClassTarget.GROUP -> form.groupId != null
        }

    /**
     * Offering a dropdown that can only be set to what it already says would invite a choice that
     * isn't there, so a group-bound subject reads as fixed rather than as pickable.
     */
    val subjectPickerEnabled: Boolean get() = targetChosen && !subjectFixedByGroup

    val subjectNotice: SubjectNotice?
        get() = when {
            subjectFixedByGroup -> SubjectNotice.FIXED_BY_GROUP
            !targetChosen || subjects.isNotEmpty() -> null
            form.target == ClassTarget.GROUP -> SubjectNotice.NONE_FOR_GROUP
            else -> SubjectNotice.NONE_FOR_STUDENT
        }

    val timeProblem: TimeProblem?
        get() = when {
            form.timeFrom != null && form.timeTo != null && form.durationMinutes == null ->
                TimeProblem.RangeInvalid

            conflict != null -> TimeProblem.Taken(conflict)
            else -> null
        }

    /** Every member unticked: the lesson would be written for nobody and bill no one. */
    val allMembersExcluded: Boolean
        get() = members.isNotEmpty() && form.excludedMemberIds.containsAll(members.map { it.id })

    /**
     * How many unticked members had a class that already **billed**. Saving deletes those classes and
     * with them income the students were charged, so it is asked about first — the same courtesy
     * deleting a completed lesson gets. Zero while booking, where nothing has billed yet.
     */
    val billedDrops: Int
        get() = booked.count { it.studentId in form.excludedMemberIds && it.status.isBillable }

    /** A group was chosen but there are none to choose — worded differently from "none picked". */
    val hasNoGroups: Boolean get() = form.target == ClassTarget.GROUP && groups.isEmpty()

    /**
     * Whether saving may be attempted. A taken slot is as disqualifying as a missing field: the
     * lesson could not happen. The save re-checks against the database regardless, since the slot
     * can be taken between here and there.
     */
    val canSave: Boolean get() = form.isValid && conflict == null && !allMembersExcluded
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClassEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val classes: ClassRepository,
    private val studentsRepo: StudentRepository,
    private val groupsRepo: GroupRepository,
    private val subjectsRepo: SubjectRepository,
    private val enrollment: EnrollmentRepository,
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

    private val students: StateFlow<List<Student>> = studentsRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val form = MutableStateFlow(ClassForm())

    /**
     * Set by the mutators the **screen** calls, and by none of the ones this view model calls on its
     * own — which is why every user-facing `selectX` below is a one-liner over a private `applyX` that
     * the auto-selection in `init` uses instead. Loading a lesson to edit, pre-selecting the group the
     * form was opened from, filling in the only student or the only subject: none of those is the
     * tutor's work, so none of them may make the form look unsaved.
     */
    private val touched = MutableStateFlow(false)

    /**
     * The groups offered in the picker: the active ones, plus the group of the lesson being edited so
     * an archived group's existing lesson can still name itself.
     */
    private val groups: StateFlow<List<Group>> =
        combine(groupsRepo.observeAll(), form.map { it.groupId }.distinctUntilChanged()) { all, selected ->
            all.filter { !it.archived || it.id == selected }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Who the lesson may cover, listed under the group with a tick each. While **booking**, the
     * group's bookable roster (archived students left out). While **editing**, that roster *plus*
     * whoever the lesson already covers — so a member archived, or dropped from the group, since it
     * was booked is still listed, and can still be taken out of a lesson they are in.
     *
     * Offering the current roster on an old lesson is the point rather than a leak: the group is who
     * is taught, and a lesson recorded without somebody who was there is a mistake worth fixing.
     */
    private val members: StateFlow<List<Student>> =
        combine(form.map { it.groupId }.distinctUntilChanged(), session) { groupId, booked ->
            MembersKey(groupId, booked?.classes?.map { it.studentId }?.toSet())
        }.distinctUntilChanged()
            .flatMapLatest { key ->
                when {
                    key.groupId == null -> flowOf(emptyList())
                    key.bookedIds != null -> combine(
                        membersOf(key.groupId),
                        studentsRepo.observeAll(),
                    ) { bookable, all ->
                        // Filtered out of the full list rather than concatenated, so the union keeps
                        // the repository's name ordering instead of listing the strays at the end.
                        val ids = bookable.map { it.id }.toSet() + key.bookedIds
                        all.filter { it.id in ids }
                    }
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
    private val subjects: StateFlow<List<Subject>> =
        form.map { SubjectKey(it.target, it.studentId, it.groupId) }.distinctUntilChanged()
            .flatMapLatest { key ->
                when {
                    key.target == ClassTarget.STUDENT && key.studentId != null ->
                        combine(
                            enrollment.observePricesFor(key.studentId),
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
    private val subjectFixedByGroup: StateFlow<Boolean> =
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
    private val conflict: StateFlow<TimeConflict?> =
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

    /**
     * The whole form, as one value. Assembled in two stages only because `combine` takes at most
     * five typed sources — the split is arithmetic, not meaning.
     */
    private data class Pickers(
        val students: List<Student>,
        val groups: List<Group>,
        val members: List<Student>,
        val subjects: List<Subject>,
        val subjectFixedByGroup: Boolean,
    )

    val state: StateFlow<ClassFormState> =
        combine(
            combine(students, groups, members, subjects, subjectFixedByGroup, ::Pickers),
            form,
            conflict,
            touched,
            session,
        ) { pickers, f, clash, isTouched, booked ->
            ClassFormState(
                form = f,
                isNew = isNew,
                students = pickers.students,
                groups = pickers.groups,
                members = pickers.members,
                subjects = pickers.subjects,
                subjectFixedByGroup = pickers.subjectFixedByGroup,
                booked = booked?.classes.orEmpty(),
                conflict = clash,
                isDirty = isTouched,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ClassFormState(isNew = isNew),
        )

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
            applyTarget(ClassTarget.GROUP)
            applyGroup(preselectedGroupId)
        }
        viewModelScope.launch { defaultLessonMinutes = settingsRepo.get().defaultLessonMinutes.toLong() }
        // Auto-select the only student there is (a new class, before any choice). With several
        // students, the user picks — we don't guess.
        if (isNew) {
            viewModelScope.launch {
                students.collect { list ->
                    val f = form.value
                    if (f.target == ClassTarget.STUDENT && f.studentId == null && list.size == 1) {
                        applyStudent(list.first().id)
                    }
                }
            }
        }
        // Auto-select the only subject the chosen student/group has (and pre-fill its price). With
        // several subjects, the user picks — we don't guess.
        viewModelScope.launch {
            subjects.collect { list ->
                if (hasTarget() && form.value.subjectId == null && list.size == 1) {
                    applySubject(list.first().id)
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
            price = MoneyField.of(loaded.sharedPrice ?: groupRate(c.groupId) ?: c.price),
            mode = c.mode,
            // Group members who are *not* in this lesson start unticked, so the ticks say what the
            // lesson is rather than who the group is now: opening a booked lesson and saving it again
            // has to change nothing, even after somebody joined the group since it was taught.
            excludedMemberIds = if (loaded.isGroup) {
                bookableRoster(c.groupId!!).toSet() - loaded.classes.map { it.studentId }.toSet()
            } else {
                emptySet()
            },
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
        touched.value = true
        form.value = transform(form.value)
    }

    /**
     * Switches between booking for a student and for a group. The subject and the price are cleared
     * because they belonged to the other target — a group's rate and a student's price for a subject
     * have nothing to do with each other.
     */
    fun setTarget(target: ClassTarget) {
        touched.value = true
        applyTarget(target)
    }

    private fun applyTarget(target: ClassTarget) {
        if (form.value.target == target) return
        form.value = form.value.copy(
            target = target,
            studentId = null,
            groupId = null,
            subjectId = null,
            price = MoneyField(kind = MoneyField.Kind.RATE),
        )
        // Auto-select the only group there is, mirroring the single-student case.
        if (target == ClassTarget.GROUP) {
            val only = groups.value.singleOrNull()
            if (only != null) applyGroup(only.id)
        }
    }

    /**
     * Choosing a (different) student clears the subject and price (they belonged to the previous
     * student) and pre-fills the class mode from the new student's default.
     */
    fun selectStudent(studentId: Long) {
        touched.value = true
        applyStudent(studentId)
    }

    private fun applyStudent(studentId: Long) {
        if (form.value.studentId == studentId) return
        val mode = students.value.firstOrNull { it.id == studentId }?.defaultMode ?: ClassMode.IN_PERSON
        form.value = form.value.copy(
            studentId = studentId,
            subjectId = null,
            price = MoneyField(kind = MoneyField.Kind.RATE),
            mode = mode,
        )
    }

    /**
     * Choosing a (different) group clears the subject, then pre-fills the lesson's rate and mode from
     * the group's own. The rate comes from the *group*, not from the subject, so unlike a student's it
     * is known as soon as the group is — there is nothing left to wait for.
     */
    fun selectGroup(groupId: Long) {
        touched.value = true
        applyGroup(groupId)
    }

    private fun applyGroup(groupId: Long) {
        if (form.value.groupId == groupId) return
        // A different group is a different set of people, so last group's exclusions mean nothing.
        form.value = form.value.copy(
            groupId = groupId,
            subjectId = null,
            price = MoneyField(kind = MoneyField.Kind.RATE),
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
                price = form.value.price.prefilledWith(group.price),
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
        touched.value = true
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
        touched.value = true
        applySubject(subjectId)
    }

    private fun applySubject(subjectId: Long) {
        form.value = form.value.copy(subjectId = subjectId)
        if (form.value.target == ClassTarget.STUDENT) prefillStudentPrice(subjectId)
    }

    private fun prefillStudentPrice(subjectId: Long) {
        val studentId = form.value.studentId ?: return
        viewModelScope.launch {
            val price = enrollment.priceFor(studentId, subjectId)?.amount
            // Only auto-fill an empty field — never clobber a price the user already typed.
            if (form.value.subjectId == subjectId) {
                form.value = form.value.copy(price = form.value.price.prefilledWith(price))
            }
        }
    }

    /**
     * Picking or changing the start time moves the end with it so the lesson keeps its length:
     * the current duration if an end was already set, otherwise the default lesson length. This
     * stops the end from going stale (and the duration from breaking) when the start is edited.
     */
    fun setTimeFrom(time: LocalTime) {
        touched.value = true
        val f = form.value
        val minutes = f.durationMinutes?.toLong() ?: defaultLessonMinutes
        form.value = f.copy(timeFrom = time, timeTo = time.plusMinutes(minutes))
    }

    fun setTimeTo(time: LocalTime) {
        touched.value = true
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
    /** One-shot signals from a save that could not go through. */
    enum class Event {
        /** The slot was taken between this form opening and the save — [conflict] names by whom. */
        SAVE_REFUSED_TIME_TAKEN,

        /** Every member was unticked, so the lesson would bill nobody. */
        SAVE_REFUSED_NO_MEMBERS,
    }

    val events = UiEvents<Event>()

    fun save(onDone: () -> Unit) {
        val f = form.value
        if (!f.isValid) return
        val slot = f.slot(zone) ?: return
        val start = slot.start
        val duration = slot.durationMinutes
        val isGroup = f.target == ClassTarget.GROUP
        val price = f.price.amount!!

        viewModelScope.launch {
            val existing = session.value
            // Re-checked here against the database, not against [conflict]: the save button is already
            // disabled on a clash, but the lesson in the way may have been booked — or moved here —
            // since this form was opened, and two lessons at one hour cannot both happen.
            val ignoring = existing?.classes?.map { it.id }?.toSet().orEmpty()
            if (classes.conflictsWith(slot, ignoring).isNotEmpty()) {
                // Say so rather than doing nothing: the button was enabled, so from the tutor's side
                // a silent no-op looks like the app dropped their lesson.
                events.send(Event.SAVE_REFUSED_TIME_TAKEN)
                return@launch
            }
            if (existing != null) {
                // Who the lesson covers afterwards, so one write settles both its fields and its
                // roster. Null for an individual lesson, which has no roster to reconcile.
                val roster = if (isGroup) rosterFor(existing, f) else null
                if (roster != null && roster.isEmpty()) {
                    events.send(Event.SAVE_REFUSED_NO_MEMBERS)
                    return@launch
                }
                classes.apply(
                    session = existing,
                    changes = SessionEdit(
                        subjectId = f.subjectId!!,
                        durationMinutes = duration,
                        mode = f.mode,
                        price = price,
                        // An individual class may be re-attributed to another student, which credits
                        // the new owner; a group lesson's members are reconciled by [roster] instead.
                        studentId = if (isGroup) null else f.studentId,
                    ),
                    newStart = start,
                    roster = roster,
                )
            } else {
                val studentIds =
                    if (isGroup) bookableRoster(f.groupId!!) - f.excludedMemberIds
                    else listOf(f.studentId!!)
                // A group whose every member has been archived (or been unticked) has nobody left
                // to bill; the lesson would be a row belonging to no one, so there is nothing to book.
                if (studentIds.isEmpty()) {
                    events.send(Event.SAVE_REFUSED_NO_MEMBERS)
                    return@launch
                }
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
            // Nothing is unsaved any more, so leaving must not ask about it.
            touched.value = false
            onDone()
        }
    }

    /**
     * Who [session] is for after this save: everyone the form offers who is still ticked.
     *
     * The offer is the group's bookable roster **plus** whoever the lesson already covers — the same
     * union [members] lists — so unticking a member who has since been archived still removes them.
     * Read from the repository rather than from [members] for the reason [bookableRoster] gives.
     */
    private suspend fun rosterFor(session: ClassSession, form: ClassForm): Set<Long> {
        val offered = bookableRoster(form.groupId!!).toSet() + session.classes.map { it.studentId }
        return offered - form.excludedMemberIds
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
