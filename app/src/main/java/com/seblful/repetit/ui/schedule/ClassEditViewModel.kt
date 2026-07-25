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
 * The form covers both kinds of booking. For [ClassTarget.STUDENT] it is one student and one
 * [priceText]. For [ClassTarget.GROUP] it is a group and [memberPrices] — one rate per member,
 * pre-filled from each member's own price for the subject, because a group lesson is billed person by
 * person (one shared price would silently re-rate somebody). Everything else — subject, date, time,
 * mode — is shared by the whole lesson.
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
    /** studentId → raw rate text, one entry per member of the chosen group. */
    val memberPrices: Map<Long, String> = emptyMap(),
    val mode: ClassMode = ClassMode.IN_PERSON,
) {
    /** Minutes between [timeFrom] and [timeTo], or null when the range isn't a positive duration. */
    val durationMinutes: Int?
        get() {
            if (timeFrom == null || timeTo == null) return null
            val minutes = Duration.between(timeFrom, timeTo).toMinutes()
            return if (minutes > 0) minutes.toInt() else null
        }

    val price: Money? get() = MoneyInput.rate(priceText)

    /** Every member's rate, or null when the roster is empty or any rate doesn't parse. */
    val memberMoney: Map<Long, Money>?
        get() {
            if (memberPrices.isEmpty()) return null
            val parsed = memberPrices.mapValues { (_, text) -> MoneyInput.rate(text) }
            return if (parsed.values.any { it == null }) null
            else parsed.mapValues { (_, money) -> money!! }
        }

    private val timingValid: Boolean
        get() = date != null && timeFrom != null && timeTo != null && (durationMinutes ?: 0) > 0

    val isValid: Boolean
        get() = subjectId != null && timingValid && when (target) {
            ClassTarget.STUDENT -> studentId != null && price != null
            ClassTarget.GROUP -> groupId != null && memberMoney != null
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
     */
    private var session: ClassSession? = null

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
     * Who the lesson's rate rows are for. While **booking**, the group's bookable roster (archived
     * students left out). While **editing**, the members the lesson was actually booked for — a
     * roster change since then must not add or drop a billed class, so the form shows the lesson as
     * it is, not as the group is now.
     */
    val members: StateFlow<List<Student>> =
        form.map { MembersKey(it.groupId, it.memberPrices.keys) }.distinctUntilChanged()
            .flatMapLatest { key ->
                when {
                    key.groupId == null -> flowOf(emptyList())
                    !isNew -> studentsRepo.observeAll().map { all -> all.filter { it.id in key.bookedIds } }
                    else -> membersOf(key.groupId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class MembersKey(val groupId: Long?, val bookedIds: Set<Long>)

    /**
     * The subjects this lesson may be booked for. For a student: those with a price set for them, so
     * the class can be billed at completion. For a group: those **every** member has a price for —
     * the group's lesson bills each member separately, so a subject one of them isn't priced for
     * could not be billed for that member (see [GroupRules.bookableSubjects]).
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
                            membersOf(key.groupId),
                            pricesRepo.observePricedSubjectsByStudent(),
                            subjectsRepo.observeAll(),
                        ) { memberList, pricedByStudent, all ->
                            GroupRules.bookableSubjects(all, memberList.map { pricedByStudent[it.id].orEmpty() })
                        }

                    else -> flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class SubjectKey(val target: ClassTarget, val studentId: Long?, val groupId: Long?)

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
        session = loaded
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
            priceText = if (loaded.isGroup) "" else c.price.toAmountInput(),
            // Each member keeps the rate their own class was booked at.
            memberPrices = if (loaded.isGroup) {
                loaded.classes.associate { it.studentId to it.price.toAmountInput() }
            } else {
                emptyMap()
            },
            mode = c.mode,
        )
    }

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
     * Switches between booking for a student and for a group. The subject and every price are cleared
     * because they belonged to the other target — a group's shared subject and a student's price have
     * nothing to do with each other.
     */
    fun setTarget(target: ClassTarget) {
        if (form.value.target == target) return
        form.value = form.value.copy(
            target = target,
            studentId = null,
            groupId = null,
            subjectId = null,
            priceText = "",
            memberPrices = emptyMap(),
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
     * Choosing a (different) group clears the subject and every member rate, and pre-fills the mode
     * from the group's default.
     */
    fun selectGroup(groupId: Long) {
        if (form.value.groupId == groupId) return
        form.value = form.value.copy(groupId = groupId, subjectId = null, memberPrices = emptyMap())
        // Read the group's mode from the repository, not from `groups`: the form can be opened
        // already pointed at a group (from that group's screen), before that flow has emitted.
        viewModelScope.launch {
            val mode = groupsRepo.getById(groupId)?.defaultMode ?: return@launch
            if (form.value.groupId == groupId) form.value = form.value.copy(mode = mode)
        }
    }

    /**
     * Selecting a subject pre-fills the rate(s) from the current price for it: the student's price,
     * or one row per group member. A member with no price for the subject gets a blank row rather
     * than being dropped, so the form stays invalid until the tutor fills it in — a missing rate can
     * never silently exclude somebody from the lesson.
     */
    fun selectSubject(subjectId: Long) {
        form.value = form.value.copy(subjectId = subjectId)
        when (form.value.target) {
            ClassTarget.STUDENT -> prefillStudentPrice(subjectId)
            ClassTarget.GROUP -> prefillMemberPrices(subjectId)
        }
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

    private fun prefillMemberPrices(subjectId: Long) {
        val groupId = form.value.groupId ?: return
        viewModelScope.launch {
            val booked = session
            val memberIds = if (booked != null) {
                // Editing: exactly the members this lesson was booked for. Re-pricing it must never
                // add or drop somebody just because the group's roster has changed since.
                booked.classes.map { it.studentId }
            } else {
                // Booking: read the roster directly instead of through `members`, so the rates cover
                // every member even when nothing is collecting that flow yet.
                val ids = groupsRepo.getMemberIds(groupId).toSet()
                val active = studentsRepo.observeActive().first().filter { it.id in ids }
                GroupRules.bookableMembers(active).map { it.id }
            }
            val prefilled = memberIds.associateWith { studentId ->
                pricesRepo.get(studentId, subjectId)?.amount?.toAmountInput() ?: ""
            }
            if (form.value.subjectId != subjectId || form.value.groupId != groupId) return@launch
            // Keep whatever the user has already typed for a member.
            val typed = form.value.memberPrices.filterValues { it.isNotBlank() }
            form.value = form.value.copy(memberPrices = prefilled + typed.filterKeys { it in prefilled })
        }
    }

    fun setMemberPrice(studentId: Long, text: String) {
        val current = form.value.memberPrices
        if (studentId !in current) return
        form.value = form.value.copy(memberPrices = current + (studentId to text))
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
     * Saves the form as one lesson. Booking a student and booking a group differ only in the rates
     * involved — one entry or one per member — and in whether the lesson names a group, so both go
     * through the same two repository writes: [ClassRepository.book] for a new lesson,
     * [ClassRepository.apply] for an edit. The rules (re-plan on a move, then rebill) run inside
     * those, so this can't leave a class carrying a stale charge.
     */
    fun save(onDone: () -> Unit) {
        val f = form.value
        if (!f.isValid) return
        val start = f.date!!.atTime(f.timeFrom!!).atZone(zone).toInstant()
        val duration = f.durationMinutes!!
        val isGroup = f.target == ClassTarget.GROUP
        // One rate per student either way: the whole roster's, or just this student's.
        val prices = if (isGroup) f.memberMoney!! else mapOf(f.studentId!! to f.price!!)

        viewModelScope.launch {
            val existing = session
            if (existing != null) {
                classes.apply(
                    session = existing,
                    changes = SessionEdit(
                        subjectId = f.subjectId!!,
                        durationMinutes = duration,
                        mode = f.mode,
                        pricesByStudent = prices,
                        // An individual class may be re-attributed to another student, which credits
                        // the new owner; a group lesson's members come from its roster instead.
                        studentId = if (isGroup) null else f.studentId,
                    ),
                    newStart = start,
                )
            } else {
                classes.book(
                    subjectId = f.subjectId!!,
                    start = start,
                    durationMinutes = duration,
                    mode = f.mode,
                    pricesByStudent = prices,
                    groupId = if (isGroup) f.groupId else null,
                )
            }
            onDone()
        }
    }
}
