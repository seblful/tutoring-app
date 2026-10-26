package com.seblful.klas.widget

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The widget sizes at which a layout changes. The layouts compare against these and the preview
 * `SizeMode.Responsive` sets are built from them, so a preview always shows each layout it can switch to.
 */

/** Today: tall enough from here for the title bar. */
internal val TodayTitleBarMinHeight = 180.dp

/** Today: narrower than this, titles are smaller and the title bar has no title. */
internal val TodaySmallWidth = 260.dp

/** Quick actions: shorter than this is a single row. */
internal val ToolBarRowMaxHeight = 128.dp

/** Quick actions: narrower than this is a single column. */
internal val ToolBarColumnMaxWidth = 128.dp

/** Quick actions: from this height the grid gets a title bar over it. */
internal val ToolBarHeaderMinHeight = 172.dp

/** Quick actions: from this width the grid and the title bar show four shortcuts and a title. */
internal val ToolBarWideMinWidth = 240.dp

/** Quick actions: smaller than this on either side, buttons are not filled. */
internal val ToolBarFilledMinSize = 72.dp

/** M3 title small, for a narrow widget; [WidgetTitleLarge] otherwise. */
internal val WidgetTitleSmall = 14.sp
internal val WidgetTitleLarge = 16.sp

/** M3 label medium. */
internal val WidgetLabel = 12.sp
