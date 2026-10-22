package com.seblful.klas.ui.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * What a button is for, which decides how loud it is. One filled [Primary] per screen at most; a loss is
 * [Destructive] (a text button in the destructive role) or [DestructiveOutlined] where it stands alone.
 */
enum class ButtonKind { Primary, Tonal, Outlined, Text, Quiet, Destructive, DestructiveOutlined }

/** A dialog's own button: what it says, whether it can be pressed, and whether pressing it loses something. */
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/** The one text action a section header may carry: "See all ›", "+ Add". */
@Immutable
data class SectionAction(val label: String, val onClick: () -> Unit)
