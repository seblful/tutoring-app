package com.seblful.klas.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.BadgeLine
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.TrailingAmount
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.lessonBadgeColor
import com.seblful.klas.ui.common.lessonBadgeLabel

/*
 * The small parts a row is made of — the status pill, the section title, the amount at a row's end —
 * and the nested row they make together. Same rules as `CommonPreviews.kt`: Russian text, plausible
 * numbers, every preview a screenshot test.
 */

/**
 * Every state a pill says, side by side: the three lesson statuses, a lesson in progress, and «В архиве».
 *
 * A badge is read by its *tint*, so the question is whether five tints stay five — the amber of «Идёт»
 * against the red of «Отменено», the archive's grey against the plan's indigo — on both grounds. The
 * light render is also where the darkened ink has to hold its contrast on its own pale wash.
 */
@KlasPreview
@Composable
private fun StatusBadgePreview() = PreviewSurface {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        LessonStatus.entries.forEach { status ->
            StatusBadge(text = classStatusLabel(status), color = classStatusColor(status))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        StatusBadge(
            text = lessonBadgeLabel(LessonStatus.SCHEDULED, running = true),
            color = lessonBadgeColor(LessonStatus.SCHEDULED, running = true),
        )
        ArchivedBadge()
    }
}

/**
 * A section title as the surfaces use it — plain over a form's group, tinted where it marks something.
 *
 * One style for every section is the rule; this is where a reviewer sees that the tinted title differs in
 * colour and in nothing else, and that `titleSmall` still reads as a heading over body text at 1.5×.
 */
@KlasPreview
@Composable
private fun SectionTitlePreview() = PreviewSurface {
    SectionTitle("Длительность")
    Text("1,5 ч · по субботам", style = MaterialTheme.typography.bodyMedium)
    SectionTitle("Уведомления", color = MaterialTheme.colorScheme.primary)
    Text("За час до занятия", style = MaterialTheme.typography.bodyMedium)
}

/**
 * The nested row: lessons inside a student's card, on a [SectionCard], each amount at the trailing edge.
 *
 * Built the way the student's card builds it — date first, the status pill leading the second line,
 * [TrailingAmount] last — because what is worth checking is the row's *ranking*: the amount must read
 * as the row's figure without outshouting the date, and at 1.5× it is the subject line that gives way,
 * not the money. The last row opens a screen, so it ends in [OpensScreenMark] instead.
 */
@KlasPreview
@Composable
private fun NestedRowPreview() = PreviewSurface {
    SectionCard {
        SectionTitle("Занятия", modifier = Modifier.padding(start = Spacing.gutter, top = Spacing.gutter))
        LessonRow(date = "Сб, 4 окт · 10:00", status = LessonStatus.COMPLETED, amount = "40,00 \u20C5")
        LessonRow(date = "Ср, 8 окт · 18:30", status = LessonStatus.SCHEDULED, amount = null)
        ListItem(
            headlineContent = { Text("Все занятия") },
            trailingContent = { OpensScreenMark() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun LessonRow(date: String, status: LessonStatus, amount: String?) {
    ListItem(
        headlineContent = { Text(date) },
        supportingContent = {
            BadgeLine(badge = { StatusBadge(classStatusLabel(status), classStatusColor(status)) }) {
                Text(
                    "Английский · индивидуально",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = { if (amount != null) TrailingAmount(amount) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
