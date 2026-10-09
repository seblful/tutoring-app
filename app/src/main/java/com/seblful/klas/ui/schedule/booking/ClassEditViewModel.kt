package com.seblful.klas.ui.schedule.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.LessonWrite
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.lesson.GroupRules
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.format.MoneyField
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.writeAwaited
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

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
            all.offered(selected, Student::archived, Student::id)
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * The groups offered in the picker: the active ones, plus the group of the lesson being edited so
     * an archived group's existing lesson can still name itself.
     */
    private val groups: StateFlow<Loadable<List<Group>>> =
        combine(groupsRepo.observeAll(), form.map { it.groupId }.distinctUntilChanged()) { all, selected ->
            all.offered(selected, Group::archived, Group::id)
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
            // length it was about to book was nowhere on screen. Whether a chip picked meanwhile wins is
            // the form's — see [BookingIntent.DefaultLength].
            if (isNew) change(BookingIntent.DefaultLength(defaultMinutes.value))
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
                // Whether it still applies by the time it is read is the form's — see
                // [BookingIntent.OnlyStudent].
                change(BookingIntent.OnlyStudent(only))
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

    /** Emits the request, or nothing at all when there is nothing to ask for. */
    private suspend fun askToBeAllowedToRemind() {
        notificationsToAskFor().takeIf { it.isNotEmpty() }?.let { askForNotifications.send(it) }
    }

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
    private suspend fun notificationsToAskFor(): List<String> =
        if (settingsRepo.get().notificationsEnabled && !notificationPermission.isGranted()) {
            notificationPermission.required
        } else {
            emptyList()
        }

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
        // A lesson that is gone has nothing to save into, and booking one in its place is not what was asked.
        if (saving.value || _gone.value) return
        val valid = ValidForm.of(currentForm, zone) ?: return
        saving.value = true

        viewModelScope.launch {
            // Read **before** the write, or the lesson just booked is the one that makes the answer false.
            // Editing an existing lesson is never a first booking, so it never asks.
            val wouldBeFirst = session.value == null && !classes.anyLessons()
            val outcome = appScope.writeAwaited { write(valid) }
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

        /**
         * What a picker offers: the active ones, plus whichever one the form has [selected] even if it has
         * been archived since — see [students]. Its accessors are passed in because `Student` and `Group`
         * share `id` and `archived` without sharing a type.
         */
        fun <T> List<T>.offered(selected: Long?, archived: (T) -> Boolean, id: (T) -> Long): List<T> =
            filter { !archived(it) || id(it) == selected }
    }
}
