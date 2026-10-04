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
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.Recurrence
import com.seblful.klas.domain.Repeat
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
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.hold
import com.seblful.klas.ui.common.ready
import com.seblful.klas.ui.common.valueOrNull
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
import java.time.Instant
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
 * The class-booking form. A new booking uses the schedule's selected day when opened from there;
 * otherwise the date starts empty. The start time is chosen by the user (the picker opens on the
 * next whole hour, see [ClassEditViewModel.startPickerDefault]).
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
    /**
     * Whether this booking writes the one lesson or a run of them — see [Repeat].
     *
     * On the form for a **new** lesson only. Repeating is a property of the act of booking rather than of
     * a lesson, and there is nothing on a lesson already in the database for it to describe: a lesson
     * loaded by [ClassEditViewModel.loadExisting] therefore keeps the default, and the edit path never
     * reads [starts].
     */
    val repeat: Repeat = Repeat.NONE,
    /** How many lessons a repeating booking writes, this one included. Meaningless while [repeat] is NONE. */
    val repeatCount: Int = Recurrence.DEFAULT_COUNT,
) {
    /**
     * The last day this booking reaches — «по 24 сентября», shown beside «Повторять» the way the lesson's
     * end is shown beside «Длительность»: a consequence of the choice, worth confirming and not worth a
     * field. Null while the booking does not repeat or has no date yet.
     */
    val repeatsUntil: LocalDate?
        get() = date?.takeIf { repeat.repeats }?.let { Recurrence.lastDate(it, repeat, repeatCount) }

    /**
     * Every instant this booking would write a lesson at, first one first. One element unless [repeat]
     * says otherwise; empty while the date or the time is still missing.
     *
     * The one place the form's wall-clock values become the *series* of instants, as [slot] is the one
     * place they become the single one — so what the tutor is shown («по 24 сентября») and what the
     * repository is handed are read off the same rule.
     */
    fun starts(zone: ZoneId): List<Instant> {
        val day = date ?: return emptyList()
        val at = timeFrom ?: return emptyList()
        return Recurrence.startsFor(day, at, repeat, repeatCount, zone)
    }

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
    /**
     * Every start this booking writes a lesson at — [slot]'s first, then the rest of the series. A single
     * booking is the one-element list, so the save has one shape rather than a branch.
     *
     * Only a **new** booking reads it: editing a lesson moves the one lesson, and [ClassForm.repeat] is
     * NONE for anything loaded from the database.
     */
    val starts: List<Instant>,
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
            // Non-empty exactly when `slot` is: both are null-guarded on the same date and time, so a form
            // that produced a slot produced a series with that slot's start at the head of it.
            starts = form.starts(zone),
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
// There is no `isNew` here on purpose. It was a field of this state that nothing ever read: the screen
// asks `viewModel.isNew` at all five of its sites, and has to, because one of them titles the app bar
// before this state exists. A copy on a value nobody consults is a second answer waiting to disagree.
data class ClassFormState(
    val form: ClassForm = ClassForm(),
    /**
     * What the three pickers **say** — the chosen row's name, resolved, or blank where nothing is chosen.
     *
     * Carried rather than looked up, which is the same argument `ScheduleRow.title` and
     * `StudentFilter.name` make. The screen used to run
     * `students.firstOrNull { it.id == form.studentId }?.name ?: ""` in the composable, three times, which
     * made the picker *lists* load-bearing for display and put an id→name lookup where no test can reach
     * it. It also had a consequence: the student list is `observeActive()`, so an **archived** student's
     * lesson — still openable, since editing is allowed in any status — drew a blank «Ученик» over a
     * lesson that has one, with `canSave` still true. Groups were already spared that, by re-adding the
     * edited lesson's group to their own list; students were not.
     */
    val studentName: String = "",
    val groupName: String = "",
    val subjectName: String = "",
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
     * A group is chosen and nobody in it can be booked — every member archived, or gone from it. The
     * roster simply did not draw, the save stayed enabled, and tapping it was refused with «Отметьте хотя
     * бы одного участника» over a form with nobody to tick.
     */
    val groupHasNoMembers: Boolean
        get() = form.target == ClassTarget.GROUP && form.groupId != null && members.isEmpty()

    /**
     * Whether saving may be attempted. A taken slot is as disqualifying as a missing field: the
     * lesson could not happen. The save re-checks against the database regardless, since the slot
     * can be taken between here and there.
     */
    override val canSave: Boolean
        get() = form.isValid && conflict == null && !allMembersExcluded && !groupHasNoMembers

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

/**
 * What a repeating booking came to, when it could not book all of it.
 *
 * Only ever built for a **partial** result: everything booked is an ordinary save and nothing booked is a
 * refusal, so this exists precisely for the outcome that is neither and that a form would otherwise have
 * to swallow. [booked] counts **lessons**, matching `SeriesWrite.lessons` — a group's evening is one
 * lesson to the tutor however many students are in the room.
 *
 * [skipped] carries the dates rather than their number because the count alone gives the tutor nothing to
 * act on: the thing they now have to do is find those Mondays and book them somewhere else.
 */
data class SeriesOutcome(val booked: Int, val skipped: List<LocalDate>)

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
    private val notificationPermission: NotificationPermission,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = clock.zone
    private val classId: Long = savedStateHandle["id"] ?: 0L

    /** Non-zero when the form was opened from a group, to book that group straight away. */
    private val preselectedGroupId: Long = savedStateHandle["group"] ?: 0L

    /**
     * The day the schedule was on when its add button was tapped — the new booking's initial date.
     * Null from anywhere else.
     */
    private val openedOnDay: LocalDate? = savedStateHandle.get<String>("day")?.let(LocalDate::parse)

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

    /**
     * Whether a save is under way or done: set on the tap, cleared only when the save is turned down.
     * Once written the screen is leaving, so it stays set.
     *
     * [conflict] goes quiet while it is set. The lesson being saved is not in [session]'s ids — a new
     * one has none yet, and a member ticked into a group lesson gets a class with a new id — so the
     * moment the write committed, the live check found it at the form's own time and drew «Занято» in
     * red under the lesson the tutor had just booked, until the screen closed. [save] also ignores a
     * second tap while it is set, which would otherwise book again and be refused by the first.
     */
    private val saving = MutableStateFlow(false)

    /**
     * Whether the app has finished putting the lesson into the form.
     *
     * A **new** lesson is ready as it stands: an empty form is the honest picture of a booking nobody has
     * filled in yet, and everything `init` does to it afterwards is a prefill into a blank field. An
     * **edited** one is not — [loadExisting] is a coroutine, so the screen was already up and drawing a
     * blank `ClassForm`: no student, no subject, today's date, the default length, and «Нет групп» in the
     * error red, over a lesson that has all five. See [Loadable].
     */
    private val filled = MutableStateFlow(isNew)

    private val _gone = MutableStateFlow(false)

    /**
     * Whether the lesson this form was opened for is no longer there — deleted since the reminder, card
     * or list that opened it was drawn. It used to open as a blank «Изменить занятие», and a save from it
     * went down the booking path and wrote a new lesson.
     */
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    /**
     * The form and every rule about how it moves — see [BookingForm], which is pure. This class holds
     * it, feeds it the picker contents it needs, and runs the lookups it asks for; it decides nothing
     * about the form itself.
     */
    private val booking = MutableStateFlow(BookingForm(form = ClassForm(date = openedOnDay.takeIf { isNew })))

    /**
     * The form as it stands, for the flows below to narrow. A plain `Flow` on purpose: a `stateIn` here
     * would hold the *initial* form until something subscribed, and the synchronous reads want the
     * current one — see [currentForm].
     */
    private val form: Flow<ClassForm> = booking.map { it.form }

    /** The form right now, without waiting for a collector. */
    private val currentForm: ClassForm get() = booking.value.form

    /**
     * The students offered in the picker: the active ones, **plus the student of the lesson being
     * edited** — the same allowance [groups] gets below, and for the same reason. A retired student's
     * lesson is still openable (editing is allowed in any status, and archiving only *cancels* what was
     * planned), so leaving them out left the lesson unable to name its own student: the field drew blank
     * over a lesson that has one, with `canSave` still true.
     *
     * [Loadable] rather than a list starting empty because the form cannot be drawn against a list that
     * has merely not been read yet: it would offer an empty picker over a lesson that has a student. A
     * `combine` over `StateFlow`s cannot wait for them — a placeholder is an emission — so the waiting
     * has to be in the value.
     */
    private val students: StateFlow<Loadable<List<Student>>> =
        combine(studentsRepo.observeAll(), form.map { it.studentId }.distinctUntilChanged()) { all, selected ->
            all.filter { !it.archived || it.id == selected }
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * The groups offered in the picker: the active ones, plus the group of the lesson being edited so
     * an archived group's existing lesson can still name itself.
     */
    private val groups: StateFlow<Loadable<List<Group>>> =
        combine(groupsRepo.observeAll(), form.map { it.groupId }.distinctUntilChanged()) { all, selected ->
            all.filter { !it.archived || it.id == selected }
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * Who the lesson may cover, listed under the group with a tick each. While **booking**, the
     * group's bookable roster (archived students left out). While **editing**, that roster *plus*
     * whoever the lesson already covers — so a member archived, or dropped from the group, since it
     * was booked is still listed, and can still be taken out of a lesson they are in.
     *
     * Offering the current roster on an old lesson is the point rather than a leak: the group is who
     * is taught, and a lesson recorded without somebody who was there is a mistake worth fixing.
     */
    private val members: StateFlow<Loadable<List<Student>>> =
        combine(form.map { it.groupId }.distinctUntilChanged(), session) { groupId, booked ->
            MembersKey(groupId, booked?.classes?.map { it.studentId }?.toSet())
        }.distinctUntilChanged()
            .flatMapLatest { key ->
                if (key.groupId == null) flowOf(emptyList()) else offeredRoster(key.groupId, key.bookedIds)
            }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /** [bookedIds] is null while booking something new, and the lesson's members while editing one. */
    private data class MembersKey(val groupId: Long?, val bookedIds: Set<Long>?)

    /**
     * The subjects this lesson may be booked for. For a **student**: those with a price set for them,
     * so the class can be billed at completion. For a **group** that has a subject: only that one —
     * the group is taught it, and every member is priced for it. For a group without one: all of
     * them, because nothing on the group says otherwise.
     */
    private val subjects: StateFlow<Loadable<SubjectChoice>> =
        form.map { SubjectKey.of(it) }.distinctUntilChanged()
            .flatMapLatest { subjectsFor(it) }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * The subject question, answered whole: which subjects this lesson may be booked for, **and** whether
     * the group has already decided which.
     *
     * One value because it is one read. `fixedByGroup` used to be a flow of its own, keyed on the same
     * `groupId` and calling the same `observeById` — two live queries onto one column, deriving the two
     * complementary halves of one answer, and a fifth thing the form had to wait for before it could draw
     * anything at all.
     */
    private data class SubjectChoice(
        val subjects: List<Subject> = emptyList(),
        /**
         * Whether the subject is the group's rather than this lesson's — true once the chosen group has
         * one. Drives the picker's enabled state: offering a dropdown that can only be set to what it
         * already says would invite a choice that isn't there.
         */
        val fixedByGroup: Boolean = false,
    )

    /**
     * The bookable subjects for one target, as a flow. Extracted so the shared [subjects] and the
     * auto-selection in [autoSelectOnlySubject] read **one** derivation — the alternative was for the
     * auto-selection to collect [subjects] for the view model's whole life, which would have kept its
     * `WhileSubscribed` timeout from ever firing and quietly held the queries behind it open.
     */
    private fun subjectsFor(key: SubjectKey): Flow<SubjectChoice> = when {
        key.target == ClassTarget.STUDENT && key.studentId != null ->
            combine(
                enrollment.observePricesFor(key.studentId),
                subjectsRepo.observeAll(),
            ) { prices, all ->
                // Nothing about a student fixes the subject; their prices only say which are billable.
                SubjectChoice(Scheduling.assignableSubjects(all, prices.map { it.subjectId }))
            }

        key.target == ClassTarget.GROUP && key.groupId != null ->
            combine(
                groupsRepo.observeById(key.groupId),
                subjectsRepo.observeAll(),
            ) { group, all ->
                val bound = group?.subjectId
                SubjectChoice(
                    subjects = if (bound == null) all else all.filter { it.id == bound },
                    fixedByGroup = bound != null,
                )
            }

        else -> flowOf(SubjectChoice())
    }


    /**
     * The lesson already booked at the time the form points at, or null when that time is free. Live,
     * so a clash shows up the moment a date or time is picked instead of on save — the tutor is one
     * person and cannot teach two lessons at once.
     *
     * The lesson being **edited** is excluded by every one of its class ids, not just the one that was
     * tapped: a group lesson would otherwise be found colliding with its own other members.
     *
     * The one flow here **not** wrapped in [Loadable], and deliberately: its null is the absence of a
     * warning rather than a claim about the tutor's data, and the repository refuses a taken hour on save
     * regardless (`LessonWrite.HourTaken`), so an unread answer cannot let a double booking through.
     *
     * **It answers about the lesson on the form, which for a repeating booking is the first occurrence
     * only.** That is the honest scope for a live warning: the form shows one date and one time, and
     * flagging a clash three Mondays away would point at a field that does not say that. The rest of the
     * run is answered where it is decided — `bookSeries` books every free hour and reports the ones it
     * could not ([SeriesOutcome]), so a later clash is named after the fact rather than guessed at before
     * it.
     *
     * Silent while [saving]: the form is then the lesson being written, not a question about the hour.
     */
    private val conflict: StateFlow<TimeConflict?> =
        saving.flatMapLatest { underWay -> if (underWay) flowOf(null) else liveConflict() }
            .derive(this, computation, null)

    private fun liveConflict(): Flow<TimeConflict?> =
        combine(
            form.map { it.slot(zone) }.distinctUntilChanged(),
            // The lesson itself, not its class ids: which of them a clash scan has to skip is
            // `ClassSession.ids`' to derive, and this was the fourth place deriving it by hand.
            // Already a `StateFlow`, so it conflates equal values itself.
            session,
        ) { slot, editing -> slot to editing }
            .flatMapLatest { (slot, editing) ->
                if (slot == null) {
                    flowOf(null)
                } else {
                    combine(
                        classes.observeConflicts(slot, editing),
                        studentsRepo.observeAll(),
                        groupsRepo.observeAll(),
                    ) { clashing, allStudents, allGroups ->
                        clashing.firstOrNull()?.let { describe(it, allStudents, allGroups) }
                    }
                }
            }

    /**
     * The whole form, as one value. Assembled in two stages only because `combine` takes at most
     * five typed sources — the split is arithmetic, not meaning.
     */
    private data class Pickers(
        val students: List<Student>,
        val groups: List<Group>,
        val members: List<Student>,
        val subject: SubjectChoice,
    )

    /**
     * The four pickers as one value, once **all four** have been read.
     *
     * `return@combine` on the first one missing rather than four nested `when`s: the answer is the same
     * whichever it is, and there is no partly-drawable form — a subject list that has arrived is no use
     * while the student list it names the lesson's student from has not.
     *
     * It was five. The fifth was a boolean about the group's subject, read by a second query onto the
     * column [SubjectChoice] already answers from.
     */
    private val pickers: Flow<Loadable<Pickers>> =
        combine(students, groups, members, subjects) { s, g, m, subj ->
            Loadable.Ready(
                Pickers(
                    students = s.valueOrNull() ?: return@combine Loadable.Loading,
                    groups = g.valueOrNull() ?: return@combine Loadable.Loading,
                    members = m.valueOrNull() ?: return@combine Loadable.Loading,
                    subject = subj.valueOrNull() ?: return@combine Loadable.Loading,
                ),
            )
        }

    val state: StateFlow<Loadable<ClassFormState>> =
        combine(
            pickers,
            booking,
            conflict,
            session,
            filled,
        ) { readPickers, held, clash, booked, isFilled ->
            // Both halves: the lesson has been put in the form, and the lists the form names it out of
            // have been read. Either one alone still draws a lesson that is not this lesson.
            val pickers = readPickers.valueOrNull()?.takeIf { isFilled } ?: return@combine Loadable.Loading
            val f = held.form
            val isTouched = held.touched
            Loadable.Ready(
                ClassFormState(
                    form = f,
                    // The names the pickers show, resolved here rather than in the composable. Blank where
                    // nothing is chosen, which is what an unfilled picker says. Spelled three times
                    // rather than through one helper because `Student`, `Group` and `Subject` share `id`
                    // and `name` without sharing a type, and a `Named` interface on the domain to save
                    // two lines here would be the wrong way round — what matters is that all three are
                    // in the module the rules are in, not that they are one function.
                    studentName = pickers.students.firstOrNull { it.id == f.studentId }?.name.orEmpty(),
                    groupName = pickers.groups.firstOrNull { it.id == f.groupId }?.name.orEmpty(),
                    subjectName = pickers.subject.subjects.firstOrNull { it.id == f.subjectId }?.name.orEmpty(),
                    students = pickers.students,
                    groups = pickers.groups,
                    members = pickers.members,
                    subjects = pickers.subject.subjects,
                    subjectFixedByGroup = pickers.subject.fixedByGroup,
                    booked = booked?.classes.orEmpty(),
                    conflict = clash,
                    isDirty = isTouched,
                ),
            )
        }
            .derive(this, computation, Loadable.Loading)

    /** Archived students and groups are looked up too — a past lesson still occupied that hour. */
    private fun describe(clash: ClassSession, students: List<Student>, groups: List<Group>): TimeConflict =
        TimeConflict.of(
            clash = clash,
            groupName = clash.groupId?.let { id -> groups.firstOrNull { it.id == id }?.name },
            studentName = students.firstOrNull { it.id == clash.first.studentId }?.name,
        )

    init {
        if (!isNew) {
            // [filled] is set outside [loadExisting] so a lesson that is no longer there still opens as a
            // form, rather than leaving the screen on a skeleton waiting for a row that will never come.
            viewModelScope.launch {
                loadExisting()
                filled.value = true
            }
        } else if (preselectedGroupId != 0L) {
            // Not the tutor's work, so neither marks the form unsaved — see [BookingIntent.isEdit].
            change(BookingIntent.ChooseTarget(ClassTarget.GROUP).asSetup())
            change(BookingIntent.ChooseGroup(preselectedGroupId).asSetup())
        }
        viewModelScope.launch {
            defaultMinutes.value = settingsRepo.get().defaultLessonMinutes
            // A new lesson shows the tutor's usual length from the start. It was left unchosen until a
            // start was picked and then filled in quietly, so the form opened with no chip lit and the
            // length it was about to book was nowhere on screen. Setup, not an edit — and only while
            // the tutor has not already picked one in the moment it took settings to load.
            if (isNew && currentForm.durationMinutes == null) {
                change(BookingIntent.LastsFor(defaultMinutes.value).asSetup())
            }
        }
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
        val loaded = classes.getSessionFor(classId) ?: run {
            _gone.value = true
            return
        }
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
                offeredRosterNow(group) - loaded.classes.mapTo(mutableSetOf()) { it.studentId }
            } else {
                emptySet()
            },
        )
        change(BookingIntent.Load(loadedForm))
    }

    private suspend fun groupRate(groupId: Long?): Money? =
        groupId?.let { groupsRepo.getById(it)?.price }

    /**
     * Who a lesson of [groupId]'s may be offered to, live — [GroupRules.offeredRoster] over the group's
     * membership and everybody. [alreadyIn] is the lesson's own members while editing one, and null
     * while booking something new.
     *
     * The rule itself is the domain's; this is the two queries it reads over. Both this and
     * [offeredRosterNow] used to spell the union themselves, in two shapes, and disagree about where the
     * archiving half of it applied.
     */
    private fun offeredRoster(groupId: Long, alreadyIn: Set<Long>?): Flow<List<Student>> =
        combine(groupsRepo.observeMemberIds(groupId), studentsRepo.observeAll()) { ids, all ->
            GroupRules.offeredRoster(all, ids.toSet(), alreadyIn.orEmpty())
        }

    /**
     * [offeredRoster] as a one-shot read, for a save. Read straight from the repository rather than from
     * [members], so a save doesn't depend on whether anything is collecting that flow yet.
     */
    private suspend fun offeredRosterNow(groupId: Long, alreadyIn: Set<Long> = emptySet()): Set<Long> =
        GroupRules.offeredRoster(studentsRepo.observeAll().first(), groupsRepo.getMemberIds(groupId).toSet(), alreadyIn)
            .mapTo(mutableSetOf()) { it.id }

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

    /**
     * What [BookingForm] may look things up in synchronously — the picker contents as they stand.
     *
     * A list that has not been read is passed as an empty one, which is what it was before [Loadable] and
     * is right here rather than a lie: this context answers "is the student I was just handed in the
     * list", and the form already copes with the answer being no — `init` passes the only student along as
     * `known` for exactly that reason. The distinction that matters is about what the *screen draws*, and
     * that is [state]'s.
     */
    private fun bookingContext() = BookingContext(
        students = students.value.valueOrNull().orEmpty(),
        groups = groups.value.valueOrNull().orEmpty(),
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
                is Lookup.OnlySubjectFor -> subjectsFor(lookup.askedFor).first().subjects.singleOrNull()
                    ?.let { change(BookingIntent.OnlySubject(lookup.askedFor, it.id)) }
            }
        }
    }

    /**
     * What the date picker opens on: the date already chosen, else the day the schedule was showing when
     * the booking was opened (paged to next Thursday, the add button books into next Thursday), else
     * today — by the injected clock, like the two time defaults below it. This was the one picker default
     * left reading the platform in a composable.
     */
    fun datePickerDefault(today: LocalDate = LocalDate.now(clock)): LocalDate =
        currentForm.date ?: openedOnDay ?: today

    /**
     * What the start-time picker opens on: the start already chosen, or — for a lesson whose time is
     * still empty — **the next whole hour**. [now] is a parameter so the rule can be tested without
     * depending on the wall clock.
     *
     * It used to offer the current time to the minute, on the reasoning that a booking is written down
     * as it is arranged. But a lesson is *arranged* at 15:23 and *held* at 16:00: tutoring runs on the
     * hour, so the minutes were a number the tutor had to spin away every single time, and a start of
     * 15:23 is one already slightly in the past. Rounding up gives the common case nothing to do.
     *
     * Already on the hour stays there — 15:00 opens on 15:00, not 16:00 — because that time is neither
     * past nor short of the hour. The test is the **minute**, not equality with the truncated time:
     * seconds are below anything the picker can show, so 15:00:20 is still the 15:00 hour and must not
     * be pushed an hour on. Late in the evening the next hour wraps to 00:00, which is the hour it is;
     * the date is a separate field, so nothing is claimed about *which* midnight.
     */
    fun startPickerDefault(now: LocalTime = LocalTime.now(clock)): LocalTime {
        val chosen = currentForm.timeFrom
        if (chosen != null) return chosen
        val onTheHour = now.truncatedTo(ChronoUnit.HOURS)
        return if (now.minute == 0) onTheHour else onTheHour.plusHours(1)
    }

    /**
     * What «Другая» opens on: the length already chosen, otherwise the tutor's default lesson — the same
     * length picking a start would have applied, so the dialog starts where the chips left off.
     */
    fun durationPickerDefault(): Int = currentForm.durationMinutes ?: defaultMinutes.value

    /** What «Другое» opens on for the number of lessons: whatever the form already says. */
    fun repeatCountPickerDefault(): Int = currentForm.repeatCount

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
         * A repeating booking where **every** occurrence's hour was taken, so nothing was written at all.
         *
         * Distinct from [SAVE_REFUSED_TIME_TAKEN], which says "this time is taken" about the one time on
         * the form. Said about twelve dates that would be a claim the tutor cannot check against anything
         * they can see, so this one names the situation instead: nothing was booked, and the run of dates
         * is the thing to change.
         */
        SERIES_REFUSED_ALL_TAKEN,

        /**
         * The write itself failed — a database or calendar error rather than a rule. Distinct from the
         * two refusals above because there is nothing the tutor can change about it: the answer is
         * "that didn't save, try again", not "pick another time".
         */
        SAVE_FAILED,
    }

    val events = UiEvents<Event>()

    /**
     * A repeating booking that went through with **some** of its occurrences left out, and what to say
     * about it. Null when there is nothing to report — which is every single booking and every series
     * that got every date it asked for.
     *
     * **State, and not a [UiEvents] message, because the form is leaving.** A successful save navigates
     * back, and a snackbar raised on a screen that is being popped is shown to nobody. What is owed here
     * is more than a passing note anyway: a skipped occurrence is a lesson the tutor believes they have
     * booked and has not, so it is a dialog they dismiss, and the save holds the navigation until they do
     * ([acknowledgeSeries]).
     */
    private val _seriesOutcome = MutableStateFlow<SeriesOutcome?>(null)
    val seriesOutcome: StateFlow<SeriesOutcome?> = _seriesOutcome.asStateFlow()

    /**
     * Dismisses the series report. The screen navigates back afterwards — the lessons are written either
     * way, so this acknowledges rather than decides.
     */
    fun acknowledgeSeries() {
        _seriesOutcome.value = null
    }

    /** What a save attempt settled on: written, or turned down for a reason worth saying. */
    private sealed interface SaveOutcome {
        data object Saved : SaveOutcome

        /** Written, but not all of it — [outcome] is what the tutor has to be told before leaving. */
        data class SavedPartly(val outcome: SeriesOutcome) : SaveOutcome

        data class Refused(val reason: Event) : SaveOutcome
    }

    /**
     * The permissions to ask for once the tutor's **first** lesson is in the database, or empty when
     * there is nothing to ask — see [notificationsToAskFor].
     *
     * A one-shot event rather than state, and carrying the permission list rather than a bare signal, so
     * the screen owns only the launcher: which permissions exist to be asked for is the port's answer, the
     * same one `SettingsScreen` takes from `notificationPermissionsRequired`.
     */
    val askForNotifications = UiEvents<List<String>>()

    /**
     * What to request after a first booking: the notification permissions, or nothing.
     *
     * **Why here and not on first launch.** A reminder is the app's promise to warn the tutor before a
     * lesson, and until a lesson exists that promise is abstract — the permission dialog over an empty
     * schedule asks about a feature they have not met, which is what `docs/calendar-and-reminders.md`
     * means by *asked for where reminders are*. The booking is where it stops being abstract: they have
     * just written down a time they intend to be somewhere, and the app is offering to remind them of it.
     *
     * **Why only the first.** On Android 13+ a second denial is permanent, after which the dialog never
     * appears again and even the Settings row can only open the system page. So this asks once, at the
     * best moment there is, and every later chance is the row in Settings — which is a place the tutor
     * goes deliberately, not a dialog thrown at them for booking a lesson.
     *
     * Silent when reminders are switched off: the tutor has said they do not want them, and a permission
     * for a feature nobody asked for is the dialog this whole rule exists to avoid.
     */
    /** Emits the request, or nothing at all when there is nothing to ask for. */
    private suspend fun askToBeAllowedToRemind() {
        notificationsToAskFor().takeIf { it.isNotEmpty() }?.let { askForNotifications.send(it) }
    }

    private suspend fun notificationsToAskFor(): List<String> =
        if (settingsRepo.get().notificationsEnabled && !notificationPermission.isGranted()) {
            notificationPermission.required
        } else {
            emptyList()
        }

    fun save(onDone: () -> Unit) {
        // A lesson that is gone has nothing to save into, and booking one in its place is not what was asked.
        if (saving.value || _gone.value) return
        val valid = ValidForm.of(currentForm, zone) ?: return
        saving.value = true

        viewModelScope.launch {
            // Read **before** the write, or the lesson just booked is the one that makes the answer false.
            // Editing an existing lesson is never a first booking, so it never asks.
            val wouldBeFirst = session.value == null && !classes.anyLessons()
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
                // Turned down, so the tutor is back on the form and the hour is a live question again —
                // for a taken one, [conflict] is what names the lesson in the way.
                null -> {
                    saving.value = false
                    events.send(Event.SAVE_FAILED)
                }
                is SaveOutcome.Refused -> {
                    saving.value = false
                    events.send(settled.reason)
                }
                // Both written cases clear `touched` first: the lessons are in the database, so leaving
                // must not go on asking whether to save them. They differ only in who closes the screen —
                // the partial one hands that to the dialog it raises.
                is SaveOutcome.SavedPartly -> {
                    booking.value = booking.value.copy(touched = false)
                    // Before the dialog, so the two do not arrive on top of each other: the skipped
                    // dates are what the tutor has to act on, and they are still there afterwards.
                    if (wouldBeFirst) askToBeAllowedToRemind()
                    _seriesOutcome.value = settled.outcome
                }
                SaveOutcome.Saved -> {
                    // Nothing is unsaved any more, so leaving must not ask about it.
                    booking.value = booking.value.copy(touched = false)
                    // The lesson is written, so the reminder it earns is a real one — ask now, while the
                    // screen is still up to host the dialog, and leave only once it has been answered.
                    if (wouldBeFirst) askToBeAllowedToRemind()
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
            return book(valid)
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
     * Books what [valid] describes — one lesson, or the whole run of them «Повторять» asked for.
     *
     * **One path for both**, because a single booking is a series of one ([ValidForm.starts]): the branch
     * that used to exist here would otherwise have had to be re-made in the outcome mapping below, and
     * the one-lesson case is the one that must not regress.
     *
     * The three answers [SeriesWrite] can carry map to three different things to say. Nothing written is
     * a refusal and keeps the tutor on the form — worded as "this time is taken" for a single lesson,
     * because that is what the sentence is about, and as "none of them could be booked" for a series.
     * Everything written is an ordinary save. Some written is the case this feature adds, and it is the
     * one the form cannot silently swallow.
     */
    private suspend fun book(valid: ValidForm): SaveOutcome {
        val studentIds: Collection<Long> = when (val target = valid.target) {
            is ValidForm.Target.Group -> offeredRosterNow(target.groupId) - valid.excludedMemberIds
            is ValidForm.Target.Individual -> listOf(target.studentId)
        }
        // A group whose every member has been archived (or been unticked) has nobody left
        // to bill; the lesson would be a row belonging to no one, so there is nothing to book.
        if (studentIds.isEmpty()) {
            return SaveOutcome.Refused(Event.SAVE_REFUSED_NO_MEMBERS)
        }
        val written = classes.bookSeries(
            subjectId = valid.subjectId,
            starts = valid.starts,
            durationMinutes = valid.slot.durationMinutes,
            mode = valid.mode,
            studentIds = studentIds,
            price = valid.price,
            groupId = valid.groupId,
        )
        return when {
            written.wroteNothing -> SaveOutcome.Refused(
                if (valid.starts.size > 1) Event.SERIES_REFUSED_ALL_TAKEN else Event.SAVE_REFUSED_TIME_TAKEN,
            )
            written.skipped.isEmpty() -> SaveOutcome.Saved
            else -> SaveOutcome.SavedPartly(
                SeriesOutcome(
                    booked = written.lessons,
                    // The dates, not a count: "one was skipped" leaves the tutor to find which Monday,
                    // which is the whole of what they now have to do about it.
                    skipped = written.skipped.map { it.start.atZone(zone).toLocalDate() },
                ),
            )
        }
    }

    /**
     * Who [session] is for after this save: everyone the form offers who is still ticked — the same
     * union [members] lists, so unticking a member who has since been archived still removes them.
     */
    private suspend fun rosterFor(
        session: ClassSession,
        groupId: Long,
        excludedMemberIds: Set<Long>,
    ): Set<Long> =
        offeredRosterNow(groupId, session.classes.mapTo(mutableSetOf()) { it.studentId }) - excludedMemberIds

    private companion object {
        /** The lengths a tutoring lesson usually runs to — 45 minutes to two hours. */
        val COMMON_DURATIONS = listOf(45, 60, 90, 120)
    }
}
