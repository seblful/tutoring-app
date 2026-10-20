package com.seblful.klas.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.AvatarStack
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.HeroCard
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatRow
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The shared vocabulary of `ui/common`, drawn so it can be looked at.
 *
 * These are the components every screen is assembled from, which is why they are the ones worth a
 * preview: a card's inset or a badge's weight is repeated fifteen times over, so getting one wrong is
 * fifteen screens wrong, and the way that kind of drift is caught is by seeing two of them next to each
 * other rather than a fortnight apart on different screens.
 *
 * Every string here is Russian and every number is plausible, because a preview drawn with "Lorem" or
 * "Title" answers a question nobody has. The widths these components have to survive are Cyrillic
 * widths, and the amount that decides whether a stat tile shrinks is a real tutor's monthly total.
 */

/**
 * The row of numbers a student's and a group's card lead with — the app's densest layout.
 *
 * Two of them, because the interesting property is *comparative*: the three-tile row is the group's and
 * the two-tile row is the student's, and they must read as the same component with a different number of
 * tiles rather than as two different rows. The large-text render is the one to look at — at 1.5× the
 * amount is what gives, and it should be shrinking rather than being cut.
 */
@KlasPreview
@Composable
private fun StatRowPreview() = PreviewSurface {
    Text("Группа", style = MaterialTheme.typography.labelSmall)
    StatRow(
        listOf(
            Stat(label = "Занятий", value = "128", hint = "впереди 3"),
            Stat(label = "Часов", value = "192,5"),
            Stat(label = "Заработано", value = "12 480,50 BYN"),
        ),
    )
    Text("Ученик", style = MaterialTheme.typography.labelSmall)
    StatRow(
        listOf(
            Stat(label = "Занятий", value = "24", hint = "8 ч"),
            Stat(label = "Заработано", value = "1 250,50 BYN"),
        ),
    )
}

/**
 * A lesson as the schedule draws it, and the same lesson cancelled.
 *
 * `dimmed` is the whole point of putting them together: a cancelled lesson has to recede without becoming
 * unreadable — it is still a record of something — and "still legible but no longer competing" is a
 * judgement that can only be made by eye. In dark mode especially, where dimming a container that is
 * already dark has much less room to work in.
 */
@KlasPreview
@Composable
private fun ContentCardPreview() = PreviewSurface {
    ContentCard(onClick = {}) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.leading),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = "Ирина", studentId = 3)
            Text("Ирина Петрова", style = MaterialTheme.typography.titleMedium)
            StatusBadge(
                text = classStatusLabel(LessonStatus.COMPLETED),
                color = classStatusColor(LessonStatus.COMPLETED),
            )
        }
        Text("Английский · 18:00 – 19:30", style = MaterialTheme.typography.bodyMedium)
    }
    ContentCard(onClick = {}, dimmed = true) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.leading),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = "Пётр", studentId = 7)
            Text("Пётр Ковалёв", style = MaterialTheme.typography.titleMedium)
            StatusBadge(
                text = classStatusLabel(LessonStatus.CANCELLED),
                color = classStatusColor(LessonStatus.CANCELLED),
            )
        }
        Text("Математика · 20:00 – 21:00", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The card that carries a card's one loud number, in both of its tones.
 *
 * The pair is the specification: the filled one is for money earned and the tonal one for money owed,
 * and the reason the second exists is that a debt drawn in the app's happiest colour would be
 * congratulating the tutor on income they have not received. Whether the two read as *ranked* rather than
 * merely different is the thing to check, and it differs between light and dark.
 */
@KlasPreview
@Composable
private fun HeroCardPreview() = PreviewSurface {
    HeroCard(label = "Заработано за июнь", value = "3 480,00 BYN")
    HeroCard(label = "Долг", value = "240,00 BYN", tonal = true) {
        Text("за 4 занятия", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Both empty states, which are one component and used to be four.
 *
 * The full one owns a screen and centres in it; the compact one is a block inside a list and takes only
 * the height it needs. Since the difference is entirely a matter of proportion — icon size, type scale,
 * how much air — it is close to unreviewable in source and obvious on sight.
 */
@KlasPreview
@Composable
private fun EmptyStatePreview() = PreviewSurface {
    EmptyState(
        title = "Пока нет учеников",
        detail = "Добавьте первого ученика, чтобы начать вести занятия",
        icon = KlasIcons.Outlined.Groups,
        actionLabel = "Добавить ученика",
        onAction = {},
        compact = true,
    )
    EmptyState(
        title = "На этот день занятий нет",
        detail = "Выберите другой день или запишите занятие",
        icon = KlasIcons.Outlined.Groups,
    )
}

/**
 * The segmented control, at the width that broke it.
 *
 * «Как в системе» in a third of a 360dp phone is the label this component's one-line shrink rule was
 * written for, so it is the label it is previewed with. The 1.5× render is the real test.
 */
@KlasPreview
@Composable
private fun SegmentedChoicePreview() = PreviewSurface {
    SegmentedChoice(
        options = listOf("SYSTEM", "LIGHT", "DARK"),
        selected = "SYSTEM",
        onSelect = {},
        label = { value ->
            when (value) {
                "SYSTEM" -> "Как в системе"
                "LIGHT" -> "Светлая"
                else -> "Тёмная"
            }
        },
    )
}

/**
 * Monograms: one at full size, and a roster of six overlapping at list-row size.
 *
 * The stack is here for the hairline ring that keeps overlapping faces separable — it is drawn in the
 * *page's* colour, so it is the one part of this component that can only be verified against a real
 * background, and it has much less contrast to work with in dark mode. The palette is by student id, so
 * the ids are spread to show more than one hue.
 *
 * The large-text render earns its place here for a reason particular to this component: the circle is a
 * fixed `Dp` and the monogram inside it is `sp`, so the two scale independently and the letter is what
 * grows. Whether a 1.5× «Б» still sits inside its 40dp circle is not something the source can say.
 */
@KlasPreview
@Composable
private fun AvatarPreview() = PreviewSurface {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        Avatar(name = "Аня", studentId = 0)
        Avatar(name = "Борис", studentId = 2)
        Avatar(name = "Вера", studentId = 5)
    }
    AvatarStack(
        names = listOf("Аня", "Борис", "Вера", "Галина", "Дмитрий", "Егор"),
        ids = listOf(0L, 2L, 5L, 6L, 8L, 9L),
    )
}
