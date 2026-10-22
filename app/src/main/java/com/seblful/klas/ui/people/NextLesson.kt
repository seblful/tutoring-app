package com.seblful.klas.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.HeadlineCardInset
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.MinHeadlineSize
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The lesson a student's or a group's card leads with.
 *
 * @param classId the class a tap opens.
 * @param line when it is, said as the People list says it.
 * @param running whether it has begun — said as «In progress» rather than as a time already past.
 * @param groupName for a student's group lesson, which group; null on a group's own card.
 */
data class NextLesson(
    val classId: Long,
    val line: LessonLine.Next,
    val running: Boolean,
    val subjectName: String?,
    val durationMinutes: Int,
    val groupName: String? = null,
) {
    companion object {
        fun of(
            klass: TutoringClass,
            subjectName: String?,
            groupName: String?,
            now: Instant,
            zone: TimeZone,
        ): NextLesson = NextLesson(
            classId = klass.id,
            line = LessonLine.next(klass.start, now, zone),
            running = klass.start <= now,
            subjectName = subjectName,
            durationMinutes = klass.durationMinutes,
            groupName = groupName,
        )
    }
}

/**
 * When the tutor next sees them, and the way to book another: the thing a card opened before a lesson is
 * opened for. Tapped, it opens that lesson.
 */
@Composable
fun NextLessonCard(
    next: NextLesson?,
    onOpen: (Long) -> Unit,
    onBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val label = if (next?.running == true) R.string.class_status_in_progress else R.string.student_next_lesson
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            HeadlineCardInset,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                if (next == null) {
                    Text(
                        stringResource(R.string.student_no_lesson_booked),
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    NextLessonLines(next)
                }
            }
            val book = stringResource(R.string.class_book)
            HoldToName(book) {
                FilledIconButton(onClick = onBook, modifier = Modifier.padding(start = Spacing.inline)) {
                    Icon(KlasIcons.Filled.Add, contentDescription = book)
                }
            }
        }
    }
    if (next != null) {
        val open = { onOpen(next.classId) }
        Card(onClick = open, modifier = modifier.fillMaxWidth(), colors = colors, content = content)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, content = content)
    }
}

/** When it is, in one line that shrinks a step before it breaks, then what and for how long. */
@Composable
private fun NextLessonLines(next: NextLesson) {
    Text(
        lessonLineText(next.line, timeAlways = true),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = with(LocalDensity.current) { MinHeadlineSize.toSp() },
            maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
        ),
    )
    Text(
        bulletJoin(next.subjectName.orEmpty(), durationLabel(next.durationMinutes), next.groupName.orEmpty()),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
