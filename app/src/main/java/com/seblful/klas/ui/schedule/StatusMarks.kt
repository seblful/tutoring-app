package com.seblful.klas.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel

private val MarkSize = 6.dp
private val MarkStroke = 1.5.dp

/** The gap between two marks in a cell: four of them and a «+1» fit a seventh of a phone. */
internal val MarkGap = 2.dp

/**
 * A lesson's status as a shape, its colour only confirming it: taught a filled dot, planned a ring,
 * cancelled a cross. Told apart in grayscale and by a tutor who does not see the three colours apart.
 */
@Composable
fun StatusMark(status: LessonStatus, modifier: Modifier = Modifier) {
    val color = classStatusColor(status)
    Canvas(modifier.size(MarkSize)) {
        val stroke = MarkStroke.toPx()
        when (status) {
            LessonStatus.COMPLETED -> drawCircle(color)
            LessonStatus.SCHEDULED ->
                drawCircle(color, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            LessonStatus.CANCELLED -> {
                drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}

/**
 * What the marks mean, under the month and the week. Not read aloud: every cell already says its
 * lessons' statuses in words.
 */
@Composable
fun StatusLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.gutter, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(LessonStatus.COMPLETED, LessonStatus.SCHEDULED, LessonStatus.CANCELLED).forEach { status ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusMark(status)
                Text(
                    classStatusLabel(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
