package com.seblful.klas.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.LessonWrite
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.RosterChange
import com.seblful.klas.domain.Scheduling
import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.format.MoneyField
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.LeavableForm
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.hold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Who a lesson is booked for: one student, or a whole group. */
enum class ClassTarget { STUDENT, GROUP }

/**
 * Which subjects the form may offer: whose they are.
 *
 * A value rather than three parameters because it is also what an in-flight lookup is *about* — asking
 * "does this target have exactly one subject" and then applying the answer are separated by a database
 * round trip, and the tutor can pick twice inside one. Comparing the key is how a stale answer is
 * recognised (see `BookingForm`).
 */
data class SubjectKey(val target: ClassTarget, val studentId: Long?, val groupId: Long?) {
    companion object {
        fun of(form: ClassForm): SubjectKey = SubjectKey(form.target, form.studentId, form.groupId)
    }
}

/**
 * The class-booking form. Date and times start **empty** — the user must choose them (the pickers
 * open on today and on the current time, see [ClassEditViewModel.startPickerDefault]).
 *
 * **A lesson is a start and a length**, which is what the database row is ([TutoringClass.start] and
 * [TutoringClass.durationMinutes]) and what [Scheduling.Slot] is. The end is derived for display
 * ([ends]) and stored nowhere, so it cannot go stale when the start moves, and a lesson running past
 * midnight is an ordinary lesson rather than a special case.
 *
 * This form used to hold the end as a second wall clock (`timeTo`) on one date, and paid for it: a
 * 23:00 start plus the default hour wrapped to 00:00, which read as *minus* twenty-three hours, so the
 * form refused a booking it had made itself and offered no way out but a shorter lesson. Worse, it
 * refused ones already in the database — «Перенести» moves a lesson to any date and time it likes, and
 * opening one it had moved to 23:00 showed a red error over a perfectly good lesson. There is no
 * negative length to detect here now; `Scheduling.Slot` keeps the `> 0` backstop.
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
    /** How long the lesson runs. Null until the tutor has said — see [ends] for where it ends up. */
    val durationMinutes: Int? = null,
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
    /**
     * Where the lesson ends — for the form to show, not for it to keep. Null until there is a start and
     * a length to add to it.
     *
     * Carries [Ending.nextDay] rather than only a clock time because "до 00:00" on its own is the one
     * end a tutor could misread as the start of the day they are booking.
     */
    val ends: Ending?
        get() {
            val from = timeFrom ?: return null
            val minutes = durationMinutes?.takeIf { it > 0 } ?: return null
            return Ending(
                time = from.plusMinutes(minutes.toLong()),
                nextDay = from.toSecondOfDay() + minutes * 60L >= Duration.ofDays(1).seconds,
            )
        }

    /** A lesson's end as a wall clock, and whether reaching it means passing midnight. */
    data class Ending(val time: LocalTime, val nextDay: Boolean)

    private val timingValid: Boolean
        get() = date != null && timeFrom != null && (durationMinutes ?: 0) > 0

    val isValid: Boolean
        get() = subjectId != null && timingValid && price.isValid && when (target) {
            ClassTarget.STUDENT -> studentId != null
            ClassTarget.GROUP -> groupId != null
        }

    /** Whether the lesson yet has somebody to be for — a student chosen, or a group. */
    val hasTarget: Boolean
        get() = when (target) {
            ClassTarget.STUDENT -> studentId != null
            ClassTarget.GROUP -> groupId != null
        }

    /**
     * The stretch of time this form describes, or null while the date and times are incomplete. The
     * one place the form's wall-clock values become an instant, so the conflict check and the save
     * can never disagree about when the lesson is.
     *
     * A length of zero or less is treated as unset rather than passed on: [Scheduling.Slot] would throw,
     * and "not filled in yet" is what it means from a form.
     */
    fun slot(zone: ZoneId): Scheduling.Slot? {
        val day = date ?: return null
        val from = timeFrom ?: return null
        val minutes = durationMinutes?.takeIf { it > 0 } ?: return null
        return Scheduling.Slot(day.atTime(from).atZone(zone).toInstant(), minutes)
    }
}

/**
 * A [ClassForm] that has passed validation, carrying everything a save needs **non-null**.
 *
 * The save used to re-assert the form's own validity field by field (`f.subjectId!!`,
 * `f.price.amount!!`, `f.studentId!!`) several hundred lines away from the [ClassForm.isValid] that
 * had actually proved it. The proof is worth keeping rather than restating: [of] is the one place
 * the checks happen, and what it returns cannot be a form that is missing a subject or a price.
 *
 * Being a snapshot matters as much as being non-null — the write runs outside `viewModelScope`
 * (see [ClassEditViewModel.save]), so it must not read a form the screen may still be editing.
 */
internal data class ValidForm(
    val target: Target,
    val subjectId: Long,
    val slot: Scheduling.Slot,
    val price: Money,
    val mode: ClassMode,
    val excludedMemberIds: Set<Long>,
) {
    /** Who the lesson is for — the one thing the two kinds of booking genuinely disagree about. */
    sealed interface Target {
        data class Individual(val studentId: Long) : Target
        data class Group(val groupId: Long) : Target
    }

    /** Null unless the lesson is a group's — the shape [ClassRepository] takes for `groupId`. */
    val groupId: Long? get() = (target as? Target.Group)?.groupId

    /** Null for a group lesson, whose members are reconciled by a roster instead. */
    val studentId: Long? get() = (target as? Target.Individual)?.studentId

    companion object {
        /**
         * The valid form [form] describes, or null when it is still incomplete. Mirrors
         * [ClassForm.isValid] — every check there is a `?: return null` here — so a form the screen
         * lets the tutor save is a form this accepts.
         */
        fun of(form: ClassForm, zone: ZoneId): ValidForm? = ValidForm(
            target = when (form.target) {
                ClassTarget.STUDENT -> Target.Individual(form.studentId ?: return null)
                ClassTarget.GROUP -> Target.Group(form.groupId ?: return null)
            },
            subjectId = form.subjectId ?: return null,
            slot = form.slot(zone) ?: return null,
            price = form.price.amount ?: return null,
            mode = form.mode,
            excludedMemberIds = form.excludedMemberIds,
        )
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
 * The screen used to receive seven separate flows and work out the answers itself — whether the
 * subject picker is live, whether everyone has been unticked, whether the save may proceed. Those are
 * rules about booking a lesson, not about drawing one, and none of them could be tested where they
 * were. Here they are ordinary properties over ordinary data.
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
    /**
     * The lesson already sitting where this one wants to be, and the only thing that can now be wrong
     * about a chosen time: a start and a length cannot describe an impossible one.
     *
     * There used to be a `TimeProblem` here deciding which of two complaints won — an at-or-before-start
     * end beat a clash, since the tutor could fix their own typing but not somebody else's lesson. The
     * first of those stopped existing when the form started holding a length ([ClassForm]).
     */
    val conflict: TimeConflict? = null,
    /**
     * Whether the tutor has changed anything — what leaving without saving would throw away.
     *
     * This form tracks having been *touched* rather than differing from a snapshot, unlike the group
     * form: it fills itself in as it goes (the only student, the only subject, the group's rate, the
     * default length once a start is picked), each arriving asynchronously after the screen is up, so
     * there is no one moment when "as it was opened" is settled. The cost is that typing something and
     * undoing it still counts as a change; back then asks a question whose answer is harmless either way.
     */
    override val isDirty: Boolean = false,
) : LeavableForm {
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

    /** Every member unticked: the lesson would be written for nobody and bill no one. */
    val allMembersExcluded: Boolean
        get() = members.isNotEmpty() && form.excludedMemberIds.containsAll(members.map { it.id })

    /**
     * How many unticked members had a class that already **billed**. Saving deletes those classes and
     * with them income the students were charged, so it is asked about first — the same courtesy
     * deleting a completed lesson gets. Zero while booking, where nothing has billed yet.
     */
    val billedDrops: Int
        get() = RosterChange(dropped = booked.filter { it.studentId in form.excludedMemberIds })
            .billedDropped.size

    /** A group was chosen but there are none to choose — worded differently from "none picked". */
    val hasNoGroups: Boolean get() = form.target == ClassTarget.GROUP && groups.isEmpty()

    /**
     * Whether saving may be attempted. A taken slot is as disqualifying as a missing field: the
     * lesson could not happen. The save re-checks against the database regardless, since the slot
     * can be taken between here and there.
     */
    override val canSave: Boolean get() = form.isValid && conflict == null && !allMembersExcluded

    /**
     * What tapping save should do: save, or ask about the income it would destroy first.
     *
     * The gate was a `state.billedDrops > 0` in the composable, next to an `Int` of screen state held
     * only to remember the count the question needed. Both are one answer to one question, and this is
     * where it can be tested.
     */
    val saveIntent: SaveIntent
        get() = billedDrops.let { if (it > 0) SaveIntent.ConfirmBilledDrops(it) else SaveIntent.Save }
}

/**
 * What saving this lesson means right now.
 *
 * Unticking a member whose class already **billed** deletes that class and the income with it, so the
 * save asks first — the same courtesy deleting a completed lesson gets.
 */
sealed interface SaveIntent {

    /** Nothing would be lost that the tutor has not already seen. */
    data object Save : SaveIntent

    /**
     * Ask first.
     *
     * @param billedLessons how many billed classes the save would delete — what the question counts.
     */
    data class ConfirmBilledDrops(val billedLessons: Int) : SaveIntent
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
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = clock.zone
    private val classId: Long = savedStateHandle["id"] ?: 0L

    /** Non-zero when the form was opened from a group, to book that group straight away. */
    private val preselectedGroupId: Long = savedStateHandle["group"] ?: 0L

    val isNew: Boolean = classId == 0L

    /**
     * The lesson length the tutor set in Settings. A flow rather than a plain field because the
     * duration chips are built from it: it arrives asynchronously, after the screen is already up, so
     * a screen reading it once would draw the wrong set and never correct itself.
     */
    private val defaultMinutes = MutableStateFlow(DEFAULT_LESSON_MINUTES)

    /**
     * The lengths offered as chips, shortest first.
     *
     * The form used to ask for an **end time**, through a clock dial, in a second modal. That is
     * arithmetic the tutor was doing in their head: a lesson is arranged as "an hour and a half", and
     * "ends at 15:30" is a derived fact about it. Four chips answer it in one tap and take the
     * booking flow from six taps to three.
     *
     * The tutor's own default is folded into the set, so the length they use most is always one of
     * the offered ones even if it is not a round number — and the set stays sorted and distinct so it
     * does not sprout a duplicate 60 for the common case.
     */
    val durationOptions: StateFlow<List<Int>> = defaultMinutes
        .map { (COMMON_DURATIONS + it).distinct().sorted() }
        .hold(this, (COMMON_DURATIONS + DEFAULT_LESSON_MINUTES).distinct().sorted())

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
        .derive(this, computation, emptyList())

    /**
     * The form and every rule about how it moves — see [BookingForm], which is pure. This class holds
     * it, feeds it the picker contents it needs, and runs the lookups it asks for; it decides nothing
     * about the form itself.
     */
    private val booking = MutableStateFlow(BookingForm())

    /**
     * The form as it stands, for the flows below to narrow. A plain `Flow` on purpose: a `stateIn` here
     * would hold the *initial* form until something subscribed, and the synchronous reads want the
     * current one — see [currentForm].
     */
    private val form: Flow<ClassForm> = booking.map { it.form }

    /** The form right now, without waiting for a collector. */
    private val currentForm: ClassForm get() = booking.value.form

    /**
     * The groups offered in the picker: the active ones, plus the group of the lesson being edited so
     * an archived group's existing lesson can still name itself.
     */
    private val groups: StateFlow<List<Group>> =
        combine(groupsRepo.observeAll(), form.map { it.groupId }.distinctUntilChanged()) { all, selected ->
            all.filter { !it.archived || it.id == selected }
        }
            .derive(this, computation, emptyList())

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
            .derive(this, computation, emptyList())

    /** [bookedIds] is null while booking something new, and the lesson's members while editing one. */
    private data class MembersKey(val groupId: Long?, val bookedIds: Set<Long>?)

    /**
     * The subjects this lesson may be booked for. For a **student**: those with a price set for them,
     * so the class can be billed at completion. For a **group** that has a subject: only that one —
     * the group is taught it, and every member is priced for it. For a group without one: all of
     * them, because nothing on the group says otherwise.
     */
    private val subjects: StateFlow<List<Subject>> =
        form.map { SubjectKey.of(it) }.distinctUntilChanged()
            .flatMapLatest { subjectsFor(it) }
            .derive(this, computation, emptyList())

    /**
     * The bookable subjects for one target, as a flow. Extracted so the shared [subjects] and the
     * auto-selection in [autoSelectOnlySubject] read **one** derivation — the alternative was for the
     * auto-selection to collect [subjects] for the view model's whole life, which would have kept its
     * `WhileSubscribed` timeout from ever firing and quietly held the queries behind it open.
     */
    private fun subjectsFor(key: SubjectKey): Flow<List<Subject>> = when {
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
            .derive(this, computation, false)


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
            .derive(this, computation, null)

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
            booking,
            conflict,
            session,
        ) { pickers, held, clash, booked ->
            val f = held.form
            val isTouched = held.touched
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
        }
            .derive(this, computation, ClassFormState(isNew = isNew))

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
            // Not the tutor's work, so neither marks the form unsaved — see [BookingIntent.isEdit].
            change(BookingIntent.ChooseTarget(ClassTarget.GROUP).asSetup())
            change(BookingIntent.ChooseGroup(preselectedGroupId).asSetup())
        }
        viewModelScope.launch { defaultMinutes.value = settingsRepo.get().defaultLessonMinutes }
        // Auto-select the only student there is (a new class, before any choice). With several
        // students, the user picks — we don't guess.
        if (isNew) {
            viewModelScope.launch {
                // Read straight from the repository, one emission, rather than collecting the shared
                // [students] flow forever: the form offers no way to add a student, so "the only
                // student there is" cannot become true while it is open, and a standing collector
                // would only serve to defeat that flow's `WhileSubscribed`.
                val only = studentsRepo.observeActive().first().singleOrNull() ?: return@launch
                val f = currentForm
                if (f.target == ClassTarget.STUDENT && f.studentId == null) {
                    // Pass the student along: [students] may not have emitted yet, and their default
                    // class mode is read from it.
                    change(BookingIntent.ChooseStudent(only.id, known = only).asSetup())
                }
            }
            // The only subject the starting target offers, if it offers exactly one.
            run(Lookup.OnlySubjectFor(SubjectKey.of(currentForm)))
        }
    }

    private suspend fun loadExisting() {
        val loaded = classes.getSessionFor(classId) ?: return
        session.value = loaded
        val c = loaded.first
        val local = c.start.atZone(zone)
        val from = local.toLocalTime()
        // The session's own group, taken once. `isGroup` *is* this being non-null, so branching on it
        // directly lets the roster lookup below use it without an assertion.
        val group = loaded.groupId
        val loadedForm = ClassForm(
            target = if (group != null) ClassTarget.GROUP else ClassTarget.STUDENT,
            studentId = c.studentId.takeIf { group == null },
            groupId = c.groupId,
            subjectId = c.subjectId,
            date = local.toLocalDate(),
            timeFrom = from,
            // Straight across: the row keeps a length and so does the form. Reconstructing an end time
            // here is what made a lesson «Перенести» had put at 23:00 open as an error.
            durationMinutes = c.durationMinutes,
            // The rate the lesson was booked at. A group lesson from before groups had a rate of their
            // own can have members on differing rates, and no single number is the truth about it —
            // the group's current rate is then offered instead of picking one member's arbitrarily.
            price = MoneyField.of(loaded.sharedPrice ?: groupRate(c.groupId) ?: c.price),
            mode = c.mode,
            // Group members who are *not* in this lesson start unticked, so the ticks say what the
            // lesson is rather than who the group is now: opening a booked lesson and saving it again
            // has to change nothing, even after somebody joined the group since it was taught.
            excludedMemberIds = if (group != null) {
                bookableRoster(group).toSet() - loaded.classes.map { it.studentId }.toSet()
            } else {
                emptySet()
            },
        )
        change(BookingIntent.Load(loadedForm))
    }

    private suspend fun groupRate(groupId: Long?): Money? =
        groupId?.let { groupsRepo.getById(it)?.price }

    /** The group's members that a new lesson may be booked for. */
    private fun membersOf(groupId: Long): Flow<List<Student>> =
        combine(groupsRepo.observeMemberIds(groupId), studentsRepo.observeAll()) { ids, all ->
            val memberIds = ids.toSet()
            GroupRules.bookableMembers(all.filter { it.id in memberIds })
        }

    // ---- The one way the form changes ----------------------------------------------------------

    /**
     * Applies one change to the form, and goes to look up whatever the change asks for.
     *
     * This is the whole mutable surface. There used to be eleven mutators, four of them `selectX`
     * one-liners over a private `applyX` that differed only in marking the form unsaved, and half of the
     * rules ran inside `viewModelScope.launch` blocks with staleness guards no test could reach. All of
     * that is [BookingForm] now: it returns the new form *and* the reads it wants, and answers come back
     * through this same function as their own intents — so the staleness checks are in the pure module
     * with the rules they protect.
     */
    fun change(intent: BookingIntent) {
        val applied = booking.value.change(intent, bookingContext())
        booking.value = applied.form
        applied.lookups.forEach { run(it) }
    }

    private fun bookingContext() = BookingContext(
        students = students.value,
        groups = groups.value,
        defaultLessonMinutes = defaultMinutes.value,
    )

    /**
     * Answers one [Lookup] and feeds the answer back through [apply].
     *
     * Nothing here decides anything: whether an answer still applies is [BookingForm]'s, which is why
     * each intent carries what it was asked about. A lookup with no answer — a target with several
     * subjects, a student with no price — simply sends nothing.
     */
    private fun run(lookup: Lookup) {
        viewModelScope.launch {
            when (lookup) {
                is Lookup.GroupOf ->
                    groupsRepo.getById(lookup.groupId)?.let { change(BookingIntent.GroupLoaded(it)) }

                is Lookup.PriceOf -> change(
                    BookingIntent.PriceLoaded(
                        subjectId = lookup.subjectId,
                        amount = enrollment.priceFor(lookup.studentId, lookup.subjectId)?.amount,
                    ),
                )

                // Read once rather than collected: the form offers no way to add a subject, so "the only
                // subject there is" cannot become true while it is open, and a standing collector would
                // only defeat that flow's `WhileSubscribed`.
                is Lookup.OnlySubjectFor -> subjectsFor(lookup.askedFor).first().singleOrNull()
                    ?.let { change(BookingIntent.OnlySubject(lookup.askedFor, it.id)) }
            }
        }
    }

    /**
     * What the date picker opens on: the date already chosen, or today — by the injected clock, like
     * the two time defaults below it. This was the one picker default left reading the platform in a
     * composable.
     */
    fun datePickerDefault(today: LocalDate = LocalDate.now(clock)): LocalDate =
        currentForm.date ?: today

    /**
     * What the start-time picker opens on: the start already chosen, or — for a lesson whose time is
     * still empty — the current time, since a booking is normally written down as it is arranged.
     * [now] is a parameter so the rule can be tested without depending on the wall clock.
     */
    fun startPickerDefault(now: LocalTime = LocalTime.now(clock)): LocalTime =
        currentForm.timeFrom ?: now.truncatedTo(ChronoUnit.MINUTES)

    /**
     * What «Другая» opens on: the length already chosen, otherwise the tutor's default lesson — the same
     * length picking a start would have applied, so the dialog starts where the chips left off.
     */
    fun durationPickerDefault(): Int = currentForm.durationMinutes ?: defaultMinutes.value

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

        /**
         * The write itself failed — a database or calendar error rather than a rule. Distinct from the
         * two refusals above because there is nothing the tutor can change about it: the answer is
         * "that didn't save, try again", not "pick another time".
         */
        SAVE_FAILED,
    }

    val events = UiEvents<Event>()

    /** What a save attempt settled on: written, or turned down for a reason worth saying. */
    private sealed interface SaveOutcome {
        data object Saved : SaveOutcome
        data class Refused(val reason: Event) : SaveOutcome
    }

    fun save(onDone: () -> Unit) {
        val valid = ValidForm.of(currentForm, zone) ?: return

        viewModelScope.launch {
            // The write runs in the application scope, not this one: going back the instant after
            // tapping save would otherwise cancel it partway, between the committed rows and the
            // calendar mirror that follows them. Awaiting it here is what makes leaving abandon only
            // the navigation — the write itself is already out of this scope's reach.
            //
            // Guarded inside the `async`, because a failure has to come back as a *value*. An
            // exception would cross the `await` into this scope, where nothing catches it — and the
            // application scope's own handler does not see it either, since `async` holds a failure in
            // its `Deferred` rather than reporting it. Tapping «Сохранить» on a write that failed
            // therefore crashed the app, in the one place the tutor is most sure of what they did.
            val outcome = appScope.async { runCatchingCancellable { write(valid) } }.await()
            when (val settled = outcome.getOrNull()) {
                null -> events.send(Event.SAVE_FAILED)
                is SaveOutcome.Refused -> events.send(settled.reason)
                SaveOutcome.Saved -> {
                    // Nothing is unsaved any more, so leaving must not ask about it.
                    booking.value = booking.value.copy(touched = false)
                    onDone()
                }
            }
        }
    }

    /**
     * Books or updates the lesson [valid] describes. Runs outside [viewModelScope] (see [save]), so
     * it takes the form as an already-validated snapshot rather than reading [form] as it goes: the
     * screen may be gone, and a write must not depend on state that is still moving.
     */
    private suspend fun write(valid: ValidForm): SaveOutcome {
        val existing = session.value
        val written = if (existing != null) {
            // Who the lesson covers afterwards, so one write settles both its fields and its
            // roster. Null for an individual lesson, which has no roster to reconcile.
            val roster = (valid.target as? ValidForm.Target.Group)
                ?.let { rosterFor(existing, it.groupId, valid.excludedMemberIds) }
            if (roster != null && roster.isEmpty()) {
                return SaveOutcome.Refused(Event.SAVE_REFUSED_NO_MEMBERS)
            }
            classes.apply(
                session = existing,
                changes = SessionEdit(
                    subjectId = valid.subjectId,
                    durationMinutes = valid.slot.durationMinutes,
                    mode = valid.mode,
                    price = valid.price,
                    // An individual class may be re-attributed to another student, which credits
                    // the new owner; a group lesson's members are reconciled by [roster] instead,
                    // and [ValidForm.studentId] is null for one by construction.
                    studentId = valid.studentId,
                ),
                newStart = valid.slot.start,
                now = clock.instant(),
                roster = roster,
            )
        } else {
            val studentIds = when (val target = valid.target) {
                is ValidForm.Target.Group -> bookableRoster(target.groupId) - valid.excludedMemberIds
                is ValidForm.Target.Individual -> listOf(target.studentId)
            }
            // A group whose every member has been archived (or been unticked) has nobody left
            // to bill; the lesson would be a row belonging to no one, so there is nothing to book.
            if (studentIds.isEmpty()) {
                return SaveOutcome.Refused(Event.SAVE_REFUSED_NO_MEMBERS)
            }
            classes.book(
                subjectId = valid.subjectId,
                start = valid.slot.start,
                durationMinutes = valid.slot.durationMinutes,
                mode = valid.mode,
                studentIds = studentIds,
                price = valid.price,
                groupId = valid.groupId,
            )
        }
        return when (written) {
            // The repository re-checks the hour against the database, not against [conflict]: the
            // save button is already disabled on a clash, but the lesson in the way may have been
            // booked — or moved here — since this form was opened. Said out loud rather than dropped:
            // the button was enabled, so a silent no-op looks like the app lost the lesson.
            is LessonWrite.HourTaken -> SaveOutcome.Refused(Event.SAVE_REFUSED_TIME_TAKEN)
            is LessonWrite.Written<*> -> SaveOutcome.Saved
        }
    }

    /**
     * Who [session] is for after this save: everyone the form offers who is still ticked.
     *
     * The offer is the group's bookable roster **plus** whoever the lesson already covers — the same
     * union [members] lists — so unticking a member who has since been archived still removes them.
     * Read from the repository rather than from [members] for the reason [bookableRoster] gives.
     */
    private suspend fun rosterFor(
        session: ClassSession,
        groupId: Long,
        excludedMemberIds: Set<Long>,
    ): Set<Long> {
        val offered = bookableRoster(groupId).toSet() + session.classes.map { it.studentId }
        return offered - excludedMemberIds
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

    private companion object {
        /** The lengths a tutoring lesson usually runs to — 45 minutes to two hours. */
        val COMMON_DURATIONS = listOf(45, 60, 90, 120)
    }
}
