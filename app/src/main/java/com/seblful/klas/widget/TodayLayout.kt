package com.seblful.klas.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.seblful.klas.MainActivity
import com.seblful.klas.R

/** One lesson as the Today widget draws it. */
internal data class TodayRow(
    val classId: Long,
    /** Who the lesson is with. */
    val title: String,
    /** When, and in what: «10:00 · Математика». */
    val supportingText: String,
    val taught: Boolean,
    val canMarkTaught: Boolean,
)

/** What the Today widget says, in the app's language rather than the phone's. */
internal data class TodayTexts(
    val title: String,
    val empty: String,
    val bookLesson: String,
    val markTaught: String,
    val taught: String,
)

/**
 * The Glance canonical check list layout (Android platform samples), as today's lessons: the check marks
 * a lesson taught, a taught lesson stays checked rather than leaving the list, and the row opens it.
 */
@Composable
internal fun TodayLayout(
    texts: TodayTexts,
    rows: List<TodayRow>,
    bookLesson: Action,
    openLesson: (classId: Long) -> Action,
    markTaught: (classId: Long) -> Action,
) {
    val showTitleBar = LocalSize.current.height >= 180.dp
    Scaffold(
        backgroundColor = GlanceTheme.colors.widgetBackground,
        horizontalPadding = 0.dp,
        modifier = GlanceModifier.padding(top = if (showTitleBar) 0.dp else WidgetPadding, bottom = WidgetPadding),
        titleBar = if (showTitleBar) {
            {
                TitleBar(
                    startIcon = ImageProvider(R.drawable.ic_logo),
                    title = if (isSmall()) "" else texts.title,
                    iconColor = GlanceTheme.colors.primary,
                    textColor = GlanceTheme.colors.onSurface,
                    actions = {
                        CircleIconButton(
                            imageProvider = ImageProvider(R.drawable.widget_icon_add),
                            contentDescription = texts.bookLesson,
                            contentColor = GlanceTheme.colors.secondary,
                            backgroundColor = null,
                            onClick = bookLesson,
                        )
                    },
                )
            }
        } else {
            null
        },
    ) {
        if (rows.isEmpty()) {
            NoDataContent(
                noDataIconRes = R.drawable.widget_icon_no_lessons,
                noDataText = texts.empty,
                actionButtonText = texts.bookLesson,
                actionButtonIcon = R.drawable.widget_icon_add,
                actionButtonOnClick = bookLesson,
            )
        } else {
            RoundedScrollingLazyColumn(
                items = rows,
                itemId = TodayRow::classId,
                modifier = GlanceModifier.fillMaxSize(),
                itemContentProvider = { row ->
                    LessonRow(row, texts, openLesson(row.classId), markTaught(row.classId))
                },
            )
        }
    }
}

@Composable
private fun LessonRow(row: TodayRow, texts: TodayTexts, open: Action, markTaught: Action) {
    WidgetListItem(
        // The check's tap target already spaces it from the text.
        contentSpacing = 0.dp,
        modifier = GlanceModifier.fillMaxWidth().padding(start = 2.dp, end = WidgetPadding),
        onClick = open,
        leadingContent = {
            CircleIconButton(
                imageProvider = ImageProvider(
                    if (row.taught) R.drawable.widget_icon_checked else R.drawable.widget_icon_unchecked,
                ),
                contentDescription = if (row.taught) texts.taught else texts.markTaught,
                backgroundColor = null,
                contentColor = GlanceTheme.colors.secondary,
                enabled = row.canMarkTaught && !row.taught,
                onClick = markTaught,
            )
        },
        headlineContent = {
            Text(
                text = row.title,
                maxLines = 2,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    // M3 title small on a narrow widget, title medium otherwise.
                    fontSize = if (isSmall()) 14.sp else 16.sp,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
        },
        supportingContent = {
            Text(
                text = row.supportingText,
                maxLines = 2,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.secondary),
            )
        },
    )
}

/** Below the sample's first width breakpoint: smaller titles, and no title in the title bar. */
@Composable
private fun isSmall(): Boolean = LocalSize.current.width < 260.dp

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 172, heightDp = 234)
@Preview(widthDp = 303, heightDp = 200)
@Preview(widthDp = 360, heightDp = 115)
@Composable
private fun TodayLayoutPreview() {
    TodayPreview(
        listOf(
            TodayRow(1, "Anna Kowalska", "10:00 · Maths", taught = true, canMarkTaught = false),
            TodayRow(2, "11 A", "14:30 · English", taught = false, canMarkTaught = true),
            TodayRow(3, "Ivan Petrov", "17:00 · Physics", taught = false, canMarkTaught = true),
        ),
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 172, heightDp = 234)
@Composable
private fun TodayLayoutEmptyPreview() {
    TodayPreview(emptyList())
}

@Composable
private fun TodayPreview(rows: List<TodayRow>) {
    GlanceTheme(colors = KlasWidgetColors) {
        TodayLayout(
            texts = TodayTexts("Today", "No lessons today", "Book a lesson", "Mark as taught", "Taught"),
            rows = rows,
            bookLesson = PreviewAction,
            openLesson = { PreviewAction },
            markTaught = { PreviewAction },
        )
    }
}

private val PreviewAction: Action = actionStartActivity<MainActivity>()
