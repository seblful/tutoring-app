package com.seblful.klas.ui.designsystem.component

import androidx.compose.runtime.Composable
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

/**
 * The one text action a section header may carry: "See all ›", "+ Add". [destructive] for one that loses
 * something — «Cancel day» — red as every Cancel is; [opens] for one that leads to another screen («See all ›»,
 * «Schedule ›», «Statement ›»), with the chevron the lesson sheet's «Open profile ›» has. One that only shows
 * something over this screen — the next lesson's «Open» sheet — has none.
 */
@Immutable
data class SectionAction(
    val label: String,
    val destructive: Boolean = false,
    val opens: Boolean = false,
    val onClick: () -> Unit,
)

/** One way of narrowing a list: what it is called, whether it is in force, and what choosing it does. */
class FilterOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val leading: (@Composable () -> Unit)? = null,
)

/**
 * How much a [Banner] matters. Neither is amber, which means «on now».
 */
enum class BannerKind {
    /** Something does not work until the tutor acts — notifications blocked. The error container. */
    Blocking,

    /** Worth doing, nothing broken — a backup grown old. The tertiary container. */
    Advisory,
}
