package com.seblful.klas.ui.common

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Material's step-based shrink, judged on every label of [row] at once: the largest size, from [ceiling] down
 * to [floor], at which each fits the width a label is given. The other labels are measured apart, since
 * [TextAutoSizeLayoutScope.performLayout] lays out the node's own text whatever text it is handed.
 */
internal class RowAutoSize(
    private val row: List<String>,
    private val floor: TextUnit,
    private val ceiling: TextUnit,
    private val style: TextStyle,
    private val measurer: TextMeasurer,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        largestFitting(ceiling, floor) { size -> row.all { fitsOneLine(measurer, style, constraints, it, size) } }

    override fun equals(other: Any?): Boolean =
        other is RowAutoSize && other.row == row && other.floor == floor && other.ceiling == ceiling &&
            other.style == style && other.measurer == measurer

    override fun hashCode(): Int = listOf(row, floor, ceiling, style, measurer).hashCode()
}

/** Whether [label] in [style] at [size] stays on one line, uncut, within [constraints]. */
internal fun fitsOneLine(
    measurer: TextMeasurer,
    style: TextStyle,
    constraints: Constraints,
    label: String,
    size: TextUnit,
): Boolean {
    val layout = measurer.measure(
        label,
        style.copy(fontSize = size),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = constraints,
    )
    return !layout.didOverflowWidth && !layout.didOverflowHeight && !layout.isLineEllipsized(0)
}

/** The largest size from [ceiling] down to [floor], a step at a time, at which [fits]; the floor when none is. */
fun largestFitting(ceiling: TextUnit, floor: TextUnit, fits: (TextUnit) -> Boolean): TextUnit {
    var size = ceiling.value
    while (size > floor.value && !fits(size.sp)) size -= LabelSizeStep
    return maxOf(size, floor.value).sp
}

private const val LabelSizeStep = 0.5f
