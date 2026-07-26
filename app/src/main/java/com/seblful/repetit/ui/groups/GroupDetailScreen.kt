package com.seblful.repetit.ui.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.format.formatDateTime
import com.seblful.repetit.format.formatHours
import com.seblful.repetit.ui.common.Avatar
import com.seblful.repetit.ui.common.RateHistoryList
import com.seblful.repetit.ui.common.Stat
import com.seblful.repetit.ui.common.StatRow
import com.seblful.repetit.ui.common.classStatusColor
import com.seblful.repetit.ui.common.classStatusLabel
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.ui.common.lessonsStat
import com.seblful.repetit.ui.common.text

/**
 * The two things a group's card is about. Payments are **not** among them: a balance belongs to a
 * student and spans all of their lessons, so it is read on the student's card — which is exactly
 * where a member row leads.
 */
private enum class GroupTab(val labelRes: Int) {
    STUDENTS(R.string.group_students_title),
    LESSONS(R.string.group_lessons_title),
}

/**
 * A group's card: what it is taught and for how much, how much of it has happened, who is in it and
 * which lessons they attended.
 *
 * Opening a group used to open its **edit form**, which answered "how do I change this?" and nothing
 * about "how is this going?". Editing now sits behind the pencil, where it does for a student.
 *
 * Booking is **not** offered here. A lesson is booked from the schedule, which is the one place that
 * knows what else the hour holds — there is one tutor, so a booking is a claim on their day rather
 * than an entry in a group's ledger. The schedule's add button asks who the lesson is for.
 *
 * @param onEdit opens the group form — the only place a group's roster, subject or rate changes.
 * @param onMemberClick opens a member's own card, which is where their money lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMemberClick: (Long) -> Unit,
    onLessonClick: (Long) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val group = state.group

    var tab by rememberSaveable { mutableStateOf(GroupTab.STUDENTS) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).constrainedContent(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = GroupDetailKeys.HEADER) {
                GroupHeader(
                    state = state,
                    historyExpanded = historyExpanded,
                    onToggleHistory = { historyExpanded = !historyExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item(key = GroupDetailKeys.STATS) {
                StatRow(
                    stats = groupStats(state),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            stickyHeader(key = GroupDetailKeys.TABS) {
                TabRow(
                    selectedTabIndex = tab.ordinal,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    GroupTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            text = { Text(stringResource(entry.labelRes)) },
                        )
                    }
                }
            }

            when (tab) {
                GroupTab.STUDENTS -> studentsTab(state, onMemberClick)
                GroupTab.LESSONS -> lessonsTab(state, onLessonClick)
            }
        }
    }
}

/**
 * What the group is: its subject, its rate — and, behind one tap, what that rate used to be.
 *
 * A group with no subject says so rather than showing an empty line: groups made before the subject
 * was fixed on the group genuinely have none, and «Предмет не выбран» is the prompt to set one.
 */
@Composable
private fun GroupHeader(
    state: GroupDetailState,
    historyExpanded: Boolean,
    onToggleHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val group = state.group ?: return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.subjectName ?: stringResource(R.string.group_no_subject),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.subjectName == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (group.archived) {
                Text(
                    stringResource(R.string.student_archived_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        if (group.note.isNotBlank()) {
            Text(group.note, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleHistory).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.price_per_hour, group.price.text()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                if (historyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(R.string.rate_history_expand),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(historyExpanded) {
            RateHistoryList(state.rateHistory, Modifier.padding(bottom = 8.dp))
        }
    }
}

/**
 * What the group has cost the tutor's week, and what it paid: lessons and hours count **sessions**
 * (a lesson for three is one lesson, not three), while the money sums every member's charge — see
 * `Taught.byGroup`.
 */
@Composable
private fun groupStats(state: GroupDetailState): List<Stat> {
    val totals = state.totals
    return listOf(
        lessonsStat(totals),
        Stat(label = stringResource(R.string.stat_hours), value = formatHours(totals.minutes)),
        Stat(label = stringResource(R.string.stat_earned), value = totals.earned.text()),
    )
}

/**
 * The roster, each member with how many of the group's lessons they were actually in — which a list
 * of names alone does not say, since someone left out week after week is still a member.
 */
private fun LazyListScope.studentsTab(state: GroupDetailState, onMemberClick: (Long) -> Unit) {
    if (state.members.isEmpty()) {
        item(key = GroupDetailKeys.STUDENTS_EMPTY) {
            TabEmptyState(stringResource(R.string.group_no_members))
        }
        return
    }
    items(state.members, key = { GroupDetailKeys.member(it.student.id) }) { row ->
        ListItem(
            leadingContent = { Avatar(row.student.name, row.student.id) },
            headlineContent = { Text(row.student.name) },
            supportingContent = {
                Text(
                    pluralStringResource(R.plurals.lessons_count, row.attended, row.attended),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.group_member_open),
                )
            },
            modifier = Modifier.clickable { onMemberClick(row.student.id) },
        )
    }
}

/**
 * The group's lessons, newest first — one row per **lesson**, not per member's class, which is what
 * the schedule shows too. Tapping one opens it, as tapping it in the schedule would.
 */
private fun LazyListScope.lessonsTab(state: GroupDetailState, onLessonClick: (Long) -> Unit) {
    if (state.lessons.isEmpty()) {
        item(key = GroupDetailKeys.LESSONS_EMPTY) {
            TabEmptyState(stringResource(R.string.group_lessons_empty))
        }
        return
    }
    items(state.lessons, key = { GroupDetailKeys.lesson(it.session.first.id) }) { row ->
        val status = row.session.commonStatus
        ListItem(
            headlineContent = { Text(row.session.start.formatDateTime()) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        // A mixed lesson (one absentee cancelled, the rest completed) says «Разные»
                        // rather than picking one of its members' statuses to speak for the room.
                        text = status?.let { classStatusLabel(it) }
                            ?: stringResource(R.string.class_status_mixed),
                        color = status?.let { classStatusColor(it) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        pluralStringResource(R.plurals.group_lesson_attended, row.attendees, row.attendees),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = { if (!row.charge.isZero) Text(row.charge.text()) },
            modifier = Modifier.clickable { onLessonClick(row.session.first.id) },
        )
    }
}

@Composable
private fun TabEmptyState(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

/**
 * Stable keys for the group card's one `LazyColumn`. Same rule as the student card's
 * (see [com.seblful.repetit.ui.students.StudentDetailKeys]): a student id and a class id both start
 * at 1, so keys are namespaced by kind, and the card's own furniture gets fixed names of its own.
 */
private object GroupDetailKeys {
    const val HEADER = "header"
    const val STATS = "stats"
    const val TABS = "tabs"
    const val STUDENTS_EMPTY = "students:empty"
    const val LESSONS_EMPTY = "lessons:empty"

    fun member(id: Long): String = "member:$id"
    fun lesson(id: Long): String = "lesson:$id"
}
