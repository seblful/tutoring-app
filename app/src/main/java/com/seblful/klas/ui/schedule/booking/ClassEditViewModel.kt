package com.seblful.klas.ui.schedule.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.GroupRules
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.domain.schedule.DayBounds
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.zones
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.savedDraft
import com.seblful.klas.ui.common.state.shared
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.writeAwaited
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClassEditViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val classes: ClassRepository,
    private val studentsRepo: StudentRepository,
    private val groupsRepo: GroupRepository,
    private val subjectsRepo: SubjectRepository,
    private val enrollment: EnrollmentRepository,
    private val settingsRepo: SettingsRepository,
    private val notificationPermission: NotificationPermission,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: WallClock,
) : ViewModel() {

    /** Hands over the screen's navigation arguments as the [SavedStateHandle]; the rest is injected. */
    @AssistedFactory
    @ViewModelAssistedFactoryKey(ClassEditViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): ClassEditViewModel = create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): ClassEditViewModel
    }

    private val zone: TimeZone get() = clock.zone
    private val classId: Long = savedStateHandle["id"] ?: 0L

    private val preselectedGroupId: Long = savedStateHandle["group"] ?: 0L
    private val preselectedStudentId: Long = savedStateHandle["student"] ?: 0L

    private val openedOnDay: LocalDate? = savedStateHandle.get<String>("day")?.let(LocalDate::parse)

    /** A lesson this booking copies a week later — who, subject, time, length, rate and place. */
    private val likeClassId: Long = savedStateHandle["like"] ?: 0L

    val isNew: Boolean = classId == 0L

    private val defaultMinutes = MutableStateFlow(DEFAULT_LESSON_MINUTES)

    private val session = MutableStateFlow<ClassSession?>(null)

    private val saving = MutableStateFlow(false)

    private val filled = savedStateHandle.savedDraft("booking.ready", isNew)

    private val _gone = MutableStateFlow(false)

    /**
     * Whether the lesson this form was opened for is no longer there — deleted since the reminder, card
     * or list that opened it was drawn. It used to open as a blank «Изменить занятие», and a save from it
     * went down the booking path and wrote a new lesson.
     */
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    private val restoredDraft = savedStateHandle.contains("booking.form") && filled.value
    private val booking = savedStateHandle.savedDraft(
        "booking.form", BookingForm(form = ClassForm(date = openedOnDay.takeIf { isNew })),
    )

    private val form: Flow<ClassForm> = booking.map { it.form }

    private val currentForm: ClassForm get() = booking.value.form

    // Each read once for the pickers, the subject list and the roster, which all combine them.
    private val allStudents = studentsRepo.observeAll().shared(this)
    private val allGroups = groupsRepo.observeAll().shared(this)
    private val allSubjects = subjectsRepo.observeAll().shared(this)

    private val students: StateFlow<Loadable<List<Student>>> =
        combine(allStudents, form.map { it.studentId }.distinctUntilChanged()) { all, selected ->
            all.offered(selected, Student::archived, Student::id)
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    private val groups: StateFlow<Loadable<List<Group>>> =
        combine(allGroups, form.map { it.groupId }.distinctUntilChanged()) { all, selected ->
            all.offered(selected, Group::archived, Group::id)
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    private val members: StateFlow<Loadable<List<Student>>> =
        combine(form.map { it.groupId }.distinctUntilChanged(), session) { groupId, booked ->
            MembersKey(groupId, booked?.classes?.map { it.studentId }?.toSet())
        }.distinctUntilChanged()
            .flatMapLatest { key ->
                if (key.groupId == null) flowOf(emptyList()) else offeredRoster(key.groupId, key.bookedIds)
            }
            .ready()
            .derive(this, computation, Loadable.Loading)

    private data class MembersKey(val groupId: Long?, val bookedIds: Set<Long>?)

    private val subjects: StateFlow<Loadable<SubjectChoice>> =
        form.map { SubjectKey.of(it) }.distinctUntilChanged()
            .flatMapLatest { subjectsFor(it) }
            .ready()
            .derive(this, computation, Loadable.Loading)

    private data class SubjectChoice(
        val subjects: List<Subject> = emptyList(),
        /**
         * Whether the subject is the group's rather than this lesson's — true once the chosen group has
         * one. Drives the picker's enabled state: offering a dropdown that can only be set to what it
         * already says would invite a choice that isn't there.
         */
        val fixedByGroup: Boolean = false,
    )

    private fun subjectsFor(key: SubjectKey): Flow<SubjectChoice> = when {
        key.target == ClassTarget.STUDENT && key.studentId != null ->
            combine(
                enrollment.observePricesFor(key.studentId),
                allSubjects,
            ) { prices, all ->
                // Nothing about a student fixes the subject; their prices only say which are billable.
                SubjectChoice(Scheduling.assignableSubjects(all, prices.map { it.subjectId }))
            }

        key.target == ClassTarget.GROUP && key.groupId != null ->
            combine(
                groupsRepo.observeById(key.groupId),
                allSubjects,
            ) { group, all ->
                val bound = group?.subjectId
                SubjectChoice(
                    subjects = if (bound == null) all else all.filter { it.id == bound },
                    fixedByGroup = bound != null,
                )
            }

        else -> flowOf(SubjectChoice())
    }

    private data class Conflicts(val first: TimeConflict? = null, val available: Int = 1)

    private val conflict: StateFlow<Conflicts> =
        saving.flatMapLatest { underWay -> if (underWay) flowOf(Conflicts()) else liveConflicts() }
            .derive(this, computation, Conflicts())

    private fun liveConflicts(): Flow<Conflicts> =
        combine(form, session, clock.zones()) { f, editing, zone ->
            val slot = f.slot(zone) ?: return@combine null
            // A correction that leaves a past lesson cancelled reserves no time.
            if (editing != null && !ClassRules.move(editing.first, slot.start, clock.now()).status.occupiesTime) {
                return@combine null
            }
            f.starts(zone).map { Scheduling.Slot(it, slot.durationMinutes) } to editing
        }.distinctUntilChanged().flatMapLatest { check ->
            if (check == null) {
                flowOf(Conflicts())
            } else {
                val (slots, editing) = check
                val window = Scheduling.windowFor(slots)
                combine(
                    classes.observeBetween(window.from, window.toExclusive),
                    allStudents,
                    allGroups,
                ) { existing, studentList, groupList ->
                    val clashes = slots.map { Scheduling.conflicts(it, existing, editing?.ids.orEmpty()).firstOrNull() }
                    Conflicts(
                        first = clashes.firstNotNullOfOrNull { it }?.let { describe(it, studentList, groupList) },
                        available = clashes.count { it == null },
                    )
                }
            }
        }

    /** The chosen date as the timeline draws it, without this lesson in it; null until there is a date. */
    private val timeline: StateFlow<DayTimeline?> =
        combine(form.map { it.date }.distinctUntilChanged(), session, clock.zones()) { date, editing, zone ->
            date?.let { DayKey(it, editing?.ids.orEmpty(), zone) }
        }.distinctUntilChanged()
            .flatMapLatest { key -> if (key == null) flowOf(null) else dayTimeline(key) }
            .derive(this, computation, null)

    private data class DayKey(val date: LocalDate, val ignoring: Set<Long>, val zone: TimeZone)

    private fun dayTimeline(key: DayKey): Flow<DayTimeline> {
        val bounds = DayBounds.of(key.date, key.zone)
        val wholeDay = Scheduling.Slot(bounds.start, (bounds.end - bounds.start).inWholeMinutes.toInt())
        val window = Scheduling.windowFor(wholeDay)
        return combine(
            classes.observeBetween(window.from, window.toExclusive),
            allStudents,
            allGroups,
        ) { existing, studentList, groupList ->
            val booked = Scheduling.conflicts(wholeDay, existing, key.ignoring).map { session ->
                DayTimeline.Lesson.of(
                    session,
                    groupName = session.groupId?.let { id -> groupList.firstOrNull { it.id == id }?.name },
                    studentName = studentList.firstOrNull { it.id == session.first.studentId }?.name,
                )
            }
            DayTimeline.of(key.date, key.zone, booked)
        }
    }

    private data class Pickers(
        val students: List<Student>,
        val groups: List<Group>,
        val members: List<Student>,
        val subject: SubjectChoice,
    )

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
            combine(booking, clock.zones(), timeline) { held, _, day -> held to day },
            conflict,
            session,
            filled,
        ) { readPickers, (held, day), clash, booked, isFilled ->
            // Both halves: the lesson has been put in the form, and the lists the form names it out of
            // have been read. Either one alone still draws a lesson that is not this lesson.
            val pickers = readPickers.valueOrNull()?.takeIf { isFilled } ?: return@combine Loadable.Loading
            val f = held.form
            // Only once it is the form's day: a date just changed draws no timeline rather than the last one.
            val timeline = day?.takeIf { f.date?.let { date -> DayBounds.of(date, zone) } == it.day }
                ?.including(f.slot(zone))
            val length = f.durationMinutes ?: defaultMinutes.value
            val isTouched = held.touched
            Loadable.Ready(
                ClassFormState(
                    form = f,
                    ending = f.ends(zone),
                    slot = f.slot(zone),
                    timeline = timeline,
                    suggestion = timeline?.takeIf { f.timeFrom == null }
                        ?.firstFree(length, clock.now())
                        ?.let { Scheduling.Slot(it, length) },
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
                    conflict = clash.first,
                    availableOccurrences = clash.available,
                    isDirty = isTouched,
                ),
            )
        }
            .keepingTheDayDrawn()
            .derive(this, computation, Loadable.Loading)

    /** What is drawn, when it is this form's day — not the day before one just chosen, still on screen. */
    private val drawn: ClassFormState?
        get() = state.value.valueOrNull()?.takeIf { it.form.date == currentForm.date }

    /** A new booking's «Who» list — see [TargetChoices.pick]. An edit never reads it, so never queries. */
    val targetChoices: StateFlow<List<TargetChoice>> =
        combine(
            classes.observeRecentlyTaught(clock.now(), TargetChoices.SHOWN),
            allStudents,
            allGroups,
            form.map { it.assignee }.distinctUntilChanged(),
        ) { recent, studentList, groupList, chosen ->
            TargetChoices.pick(recent, studentList, groupList, chosen, shown = Int.MAX_VALUE)
        }
            .derive(this, computation, emptyList())

    /**
     * Books for [who], from a chip or «All…» — the target and the one in it as one choice, which is what
     * replaced asking «Student or group?» first.
     */
    fun choose(who: Assignee, known: Student? = null) {
        when (who) {
            is Assignee.Student -> {
                change(BookingIntent.ChooseTarget(ClassTarget.STUDENT))
                change(BookingIntent.ChooseStudent(who.studentId, known))
            }
            is Assignee.Group -> {
                change(BookingIntent.ChooseTarget(ClassTarget.GROUP))
                change(BookingIntent.ChooseGroup(who.groupId))
            }
        }
    }

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
        } else if (!restoredDraft && preselectedGroupId != 0L) {
            // Not the tutor's work, so neither marks the form unsaved — see [BookingIntent.isEdit].
            change(BookingIntent.ChooseTarget(ClassTarget.GROUP).asSetup())
            change(BookingIntent.ChooseGroup(preselectedGroupId).asSetup())
        } else if (!restoredDraft && likeClassId != 0L) {
            viewModelScope.launch { copyALessonAWeekOn(likeClassId) }
        } else if (!restoredDraft && preselectedStudentId != 0L) {
            viewModelScope.launch {
                studentsRepo.getById(preselectedStudentId)?.let {
                    change(BookingIntent.ChooseStudent(it.id, it).asSetup())
                }
            }
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
        val untouchedNew = isNew && !restoredDraft
        if (untouchedNew && preselectedStudentId == 0L && likeClassId == 0L) {
            viewModelScope.launch {
                // Initial convenience only. Setup detours return the created student's ID explicitly.
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
        if (!restoredDraft) change(BookingIntent.Load(formOf(loaded, loaded.first.start.toLocalDateTime(zone).date)))
    }

    /**
     * A new booking of [classId]'s lesson **a week later** — one ordinary lesson, written only when booked.
     * A lesson gone since leaves the form as a plain new booking.
     */
    private suspend fun copyALessonAWeekOn(classId: Long) {
        val like = classes.getSessionFor(classId) ?: return
        val weekOn = like.first.start.toLocalDateTime(zone).date.plus(DatePeriod(days = CalendarLayout.DAYS_IN_WEEK))
        change(BookingIntent.Load(formOf(like, weekOn)).asSetup())
    }

    /** [loaded] as the form shows it, on [date]. */
    private suspend fun formOf(loaded: ClassSession, date: LocalDate): ClassForm {
        val c = loaded.first
        val from = c.start.toLocalDateTime(zone).time
        // The session's own group, taken once. `isGroup` *is* this being non-null, so branching on it
        // directly lets the roster lookup below use it without an assertion.
        val group = loaded.groupId
        val loadedForm = ClassForm(
            target = if (group != null) ClassTarget.GROUP else ClassTarget.STUDENT,
            studentId = c.studentId.takeIf { group == null },
            groupId = c.groupId,
            subjectId = c.subjectId,
            date = date,
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
        return loadedForm
    }

    private suspend fun groupRate(groupId: Long?): Money? =
        groupId?.let { groupsRepo.getById(it)?.price }

    private fun offeredRoster(groupId: Long, alreadyIn: Set<Long>?): Flow<List<Student>> =
        combine(groupsRepo.observeMemberIds(groupId), allStudents) { ids, all ->
            GroupRules.offeredRoster(all, ids.toSet(), alreadyIn.orEmpty())
        }

    private suspend fun offeredRosterNow(groupId: Long, alreadyIn: Set<Long> = emptySet()): Set<Long> =
        GroupRules.offeredRoster(allStudents.first(), groupsRepo.getMemberIds(groupId).toSet(), alreadyIn)
            .mapTo(mutableSetOf()) { it.id }

    // ---- The one way the form changes ----------------------------------------------------------

    /** Applies one change to the form, and goes to look up whatever the change asks for. */
    fun change(intent: BookingIntent) {
        val applied = booking.value.change(intent, bookingContext())
        booking.value = applied.form
        applied.lookups.forEach { run(it) }
    }

    /** Starts the lesson at [start] exactly — one the day's lessons have already left free. */
    fun startAt(start: Instant) = change(BookingIntent.StartAt(start.toLocalDateTime(zone).time))

    /** One press of the When section's stepper — see [ClassFormState.stepped]. */
    fun step(later: Boolean) {
        drawn?.stepped(later)?.let(::startAt)
    }

    /** Moves the lesson [days] on, or back when negative, keeping its time; from [datePickerDefault] without a day. */
    fun moveDay(days: Int) {
        val to = datePickerDefault().plus(days, DateTimeUnit.DAY)
        change(BookingIntent.Edit { it.copy(date = to) })
    }

    fun selectCreatedStudent(studentId: Long) {
        viewModelScope.launch {
            studentsRepo.getById(studentId)?.let { choose(Assignee.Student(it.id), known = it) }
        }
    }

    /** Re-check a missing prerequisite after returning from student or subject setup. */
    fun refreshChoices() {
        if (currentForm.subjectId == null) run(Lookup.OnlySubjectFor(SubjectKey.of(currentForm)))
    }

    private fun bookingContext() = BookingContext(
        students = students.value.valueOrNull().orEmpty(),
        groups = groups.value.valueOrNull().orEmpty(),
        defaultLessonMinutes = defaultMinutes.value,
    )

    private fun run(lookup: Lookup) {
        viewModelScope.launch {
            when (lookup) {
                is Lookup.GroupOf ->
                    groupsRepo.getById(lookup.groupId)?.let { change(BookingIntent.GroupLoaded(it)) }

                is Lookup.PriceOf -> change(
                    BookingIntent.PriceLoaded(
                        studentId = lookup.studentId,
                        subjectId = lookup.subjectId,
                        amount = enrollment.priceFor(lookup.studentId, lookup.subjectId)?.amount,
                    ),
                )

                // Setup returns re-check a missing subject through refreshChoices.
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
    fun datePickerDefault(today: LocalDate = clock.today()): LocalDate =
        currentForm.date ?: openedOnDay ?: today

    /**
     * What the time dialog opens on: the start already chosen, else the first free time the stepper would
     * take, else the hour every day used to be drawn from.
     */
    fun timePickerDefault(): LocalTime =
        currentForm.timeFrom
            ?: drawn?.suggestion?.start?.toLocalDateTime(zone)?.time
            ?: LocalTime(DayTimeline.FIRST_HOUR, 0)

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

    private val _seriesOutcome = MutableStateFlow<SeriesOutcome?>(null)
    val seriesOutcome: StateFlow<SeriesOutcome?> = _seriesOutcome.asStateFlow()

    /**
     * Dismisses the series report. The screen navigates back afterwards — the lessons are written either
     * way, so this acknowledges rather than decides.
     */
    fun acknowledgeSeries() {
        _seriesOutcome.value = null
    }

    private sealed interface SaveOutcome {
        data object Saved : SaveOutcome

        /** Written, but not all of it — [outcome] is what the tutor has to be told before leaving. */
        data class SavedPartly(val outcome: SeriesOutcome) : SaveOutcome

        data class Refused(val reason: Event) : SaveOutcome

        /** The lesson was removed while the form was open, so there is nothing to write into. */
        data object Gone : SaveOutcome
    }

    /**
     * Show the notification prompt, now that the tutor's **first** lesson is in the database — see
     * [shouldAskForNotifications]. A one-shot event rather than state: the screen shows the prompt once
     * and is leaving.
     */
    val askForNotifications = UiEvents<Unit>()

    private suspend fun askToBeAllowedToRemind() {
        if (shouldAskForNotifications()) askForNotifications.send(Unit)
    }

    private suspend fun shouldAskForNotifications(): Boolean =
        settingsRepo.get().notificationsEnabled &&
            !notificationPermission.isGranted() &&
            notificationPermission.canAsk

    /**
     * Saves the form as one lesson. Booking a student and booking a group differ only in **who** the
     * classes are written for — one student, or the group's roster — so both go through the same two
     * repository writes: [ClassRepository.book] for a new lesson, [ClassRepository.apply] for an edit.
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
                // Nothing to write into and nothing to say: the screen is already showing the lesson as
                // gone, and a form that stayed live over it would offer to book it again.
                SaveOutcome.Gone -> saving.value = false
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

    private suspend fun write(valid: ValidForm): SaveOutcome {
        // **The lesson is re-read here, not taken from [session].** That snapshot was read when the form
        // opened, and a lesson's *state* moves on underneath an open form: the upkeep pass completes one
        // whose hour has passed — and completing is what bills it — and the reminder's «Проведено» does
        // the same by hand. `apply` starts every class from the state it is handed, so saving the snapshot
        // back would un-complete a lesson that was taught and void its charge, over an edit that only
        // meant to change a price. Only the form's own fields are the form's to state.
        val editing = session.value != null
        val existing = if (editing) classes.getSessionFor(classId) else null
        if (editing && existing == null) {
            // Deleted while the form was open — from a report, or by a restore. There is nothing left to
            // edit, and an edit is not a booking: writing the snapshot back would put the lesson back as a
            // new one, at an id nobody asked for. The screen says it is gone ([gone]); this is the half
            // that stops a save making that untrue.
            _gone.value = true
            return SaveOutcome.Gone
        }
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
                now = clock.now(),
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
            // Deleted between the read above and the write's own — the same answer as the early return.
            LessonWrite.Gone -> SaveOutcome.Gone.also { _gone.value = true }
        }
    }

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
                    skipped = written.skipped.map { it.start.toLocalDateTime(zone).date },
                ),
            )
        }
    }

    private suspend fun rosterFor(
        session: ClassSession,
        groupId: Long,
        excludedMemberIds: Set<Long>,
    ): Set<Long> =
        offeredRosterNow(groupId, session.classes.mapTo(mutableSetOf()) { it.studentId }) - excludedMemberIds

    private companion object {
        /**
         * What a picker offers: the active ones, plus whichever one the form has [selected] even if it has
         * been archived since — see [students]. Its accessors are passed in because `Student` and `Group`
         * share `id` and `archived` without sharing a type.
         */
        fun <T> List<T>.offered(selected: Long?, archived: (T) -> Boolean, id: (T) -> Long): List<T> =
            filter { !archived(it) || id(it) == selected }

        /**
         * Keeps what is on screen while a day just chosen has its lessons read — a frame or two — so the day and
         * what is free in it change together, rather than the When section emptying and filling again between
         * them; the first day picked included. On opening there is nothing drawn yet, so nothing is held.
         */
        fun Flow<Loadable<ClassFormState>>.keepingTheDayDrawn(): Flow<Loadable<ClassFormState>> = flow {
            var shown: ClassFormState? = null
            collect { next ->
                val ready = next.valueOrNull()
                val reading = ready != null && ready.form.date != null && ready.timeline == null
                if (reading && shown != null) return@collect
                shown = ready
                emit(next)
            }
        }
    }
}
