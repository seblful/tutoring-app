package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The booking form's steppers: a value between a step less and a step more — the start in When, and the
 * Length, Repeat and Price rows once opened.
 */

/**
 * [value] between a tonal − and +, named [lessName] and [moreName] for whoever cannot see them. A null
 * [onLess] or [onMore] is a way the value cannot go, drawn disabled.
 */
@Composable
internal fun StepRow(
    lessName: String,
    moreName: String,
    onLess: (() -> Unit)?,
    onMore: (() -> Unit)?,
    size: Dp = RowButtonSize,
    caption: String? = null,
    value: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
    ) {
        StepButton(KlasIcons.Filled.Remove, lessName, onLess, size, caption)
        value()
        StepButton(KlasIcons.Filled.Add, moreName, onMore, size, caption)
    }
}

/**
 * A stepper's value in large numbers, over a [detail] line saying what it comes to. With [onPick], a tap on
 * it — named [pickLabel] — sets it some other way than a step at a time.
 */
@Composable
internal fun RowScope.StepValue(
    text: String,
    detail: String?,
    onPick: (() -> Unit)? = null,
    pickLabel: String? = null,
    tag: String = STEP_VALUE_TAG,
) {
    val style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum")
    val picks = onPick?.let {
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = pickLabel, role = Role.Button, onClick = it)
            .padding(vertical = Spacing.tight)
    } ?: Modifier
    Column(
        Modifier.weight(1f).then(picks).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = with(LocalDensity.current) { MinValueSize.toSp() },
                maxFontSize = style.fontSize,
            ),
        )
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A round tonal button: [icon] over an optional [caption] — the step's size — named [name]. */
@Composable
internal fun StepButton(icon: ImageVector, name: String, onClick: (() -> Unit)?, size: Dp, caption: String? = null) {
    // Held, it says where a press goes («Later, to 19:30»), as every icon button names itself.
    HoldToName(name) {
        FilledTonalIconButton(
            onClick = { onClick?.invoke() },
            modifier = Modifier.size(size),
            enabled = onClick != null,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = name, Modifier.size(StepIconSize))
                caption?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
}

/** The start's buttons in When, larger than a row's: the time is what the form is mostly for. */
internal val StartButtonSize = 56.dp

internal const val STEP_VALUE_TAG = "step_value"

private val RowButtonSize = MinTouchTarget
private val StepIconSize = 20.dp
private val MinValueSize = 16.dp
