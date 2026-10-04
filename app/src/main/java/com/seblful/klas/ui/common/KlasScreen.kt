package com.seblful.klas.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R

/**
 * Every screen in the app, from the app bar down.
 *
 * Fifteen screens each spelled this out — a scaffold, an app bar, a back arrow, a snackbar host, a
 * nested-scroll connection, a width cap. That came to eight hand-written back arrows, four hand-written
 * save buttons ([SaveAction] now) and two near-identical search toggles. None of them was wrong; what
 * was missing was anything that made a *new* screen match — and two of the fifteen (`MoreScreen`,
 * `BackupScreen`) had already drifted off the scroll behaviour every other screen had.
 * `KlasScreenSurfaceTest` makes writing the long form a failing test rather than a review note.
 *
 * ### What it owns
 *
 * - The single `Scaffold`. There is exactly one per tree — the root is a `NavigationSuiteScaffold` and
 *   nesting a second one applied the status-bar inset twice.
 * - **The insets and the width cap.** [content] is handed a `Modifier` rather than a `PaddingValues`,
 *   which is what lets this be the place `padding(insets)`, `imePadding()` and `constrainedContent()`
 *   are composed *in the right order* — the screen decides only where its scroll goes.
 * - **`imePadding()` before the scroll** ([imeAware]). Under edge-to-edge the IME is an inset rather
 *   than a resize, so a form without this has its bottom fields covered with nothing to scroll. It is a
 *   rule about ordering, and a rule about ordering cannot be honoured fifteen times.
 * - **The scroll behaviour**, chosen by [titleSize] rather than restated: pinned for the tonal change
 *   that separates bar from page, exit-until-collapsed where the title *is* the subject of the screen.
 * - **The search toggle**, including the rule that closing it clears the query — a filter left applied
 *   behind a closed box is a list missing rows for no visible reason. Two screens had their own copy.
 *
 * @param title the bar's text. `null` for a screen whose body names itself (the welcome step, a detail
 *   card that has not read its subject yet) — inventing a placeholder is the one thing a loading bar
 *   must not do.
 * @param onBack the back arrow, or `null` on a tab. A form passes its `leaveGuard`, not `onBack`.
 * @param search when non-null, the bar grows a search icon and becomes a search field when it is
 *   tapped. The toggle is state, and it is *this* function's.
 * @param messages the screen's [Messages]; its host is mounted here so no screen mounts its own.
 * @param page which page of the screen is up — a tab of «Люди», a view of the schedule — for a screen
 *   whose body is swapped for another *without scrolling*. The bar keeps count of how far content has
 *   scrolled beneath it, and a page that opens at its top has scrolled nowhere: without this, «Группы»
 *   opened under a bar (and a [ScreenHeader]) still tinted for the students list scrolled a moment ago.
 * @param content given the modifier for the screen's top-level scrollable, insets and cap applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KlasScreen(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleSize: TitleSize = TitleSize.Compact,
    search: ScreenSearch? = null,
    messages: Messages? = null,
    imeAware: Boolean = false,
    page: Any? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    // The search box, or null when there is none or it is shut — one value rather than a flag plus a
    // nullable, so the two cannot be tested separately and disagree.
    val openSearch = search?.takeIf { searching }

    val scrollBehavior = when (titleSize) {
        TitleSize.Compact -> TopAppBarDefaults.pinnedScrollBehavior()
        TitleSize.Large -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }

    // Only on a *change* of page, not on arriving: a screen returned to restores its list's position and
    // the bar's offset together, and resetting here would leave a scrolled list under an untinted bar.
    var shownPage by remember { mutableStateOf(page) }
    if (page != shownPage) {
        shownPage = page
        scrollBehavior.state.contentOffset = 0f
    }

    // A large title has two lines to spend on a name while it is large; collapsed into an ordinary bar it
    // has one, like any other — it was two there too, and a long name drew two lines of 22sp in a 64 dp
    // bar. Derived, so a scroll recomposes the title only when the answer flips.
    val titleLines by remember(titleSize) {
        derivedStateOf {
            if (titleSize == TitleSize.Large && scrollBehavior.state.collapsedFraction < 0.5f) LargeTitleLines else 1
        }
    }

    val barTitle: @Composable () -> Unit = {
        if (openSearch != null) {
            SearchField(
                query = openSearch.query,
                onQueryChange = openSearch.onQueryChange,
                placeholder = openSearch.placeholder,
            )
        } else if (title != null) {
            Text(
                title,
                // A compact title shares its row with a back arrow and up to three actions, and must give
                // way rather than push them off. See [titleLines].
                maxLines = titleLines,
                // Shrinks a few steps before it gives up a word: «Изменить занят…» beside «Сохранить» was
                // every edit form's title on a 360 dp phone, and the noun is the half that says which
                // form this is. The ceiling is the bar's own style, so a title that fits is never enlarged.
                autoSize = TextAutoSize.StepBased(
                    // In dp, like the tab labels' floor: the font scale is already in the size it starts
                    // from, and scaling the floor too left no room to shrink at 1.3×.
                    minFontSize = with(LocalDensity.current) { MinTitleSize.toSp() },
                    maxFontSize = LocalTextStyle.current.fontSize,
                ),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    val barNavigation: @Composable () -> Unit = {
        if (onBack != null) {
            HoldToName(stringResource(R.string.action_back)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        }
    }

    val barActions: @Composable RowScope.() -> Unit = {
        if (openSearch != null) {
            // Closing search clears it. The screen's own actions stay hidden while the box is open —
            // the bar is one row, and a search field plus three icons is not a row.
            HoldToName(stringResource(R.string.search_close)) {
                IconButton(onClick = { searching = false; openSearch.onClose() }) {
                    Icon(Icons.Filled.SearchOff, stringResource(R.string.search_close))
                }
            }
        } else {
            if (search != null) {
                HoldToName(stringResource(R.string.action_search)) {
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, stringResource(R.string.action_search))
                    }
                }
            }
            actions()
        }
    }

    // The compact bar's colour, animated **here** rather than inside `TopAppBar`, so a [ScreenHeader]
    // under it can be painted with the very same value — one block that tints as one when a list starts
    // passing beneath it, rather than a grey bar over a strip of page-coloured controls.
    val defaultBarColors = TopAppBarDefaults.topAppBarColors()
    val overlapped by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.overlappedFraction > OverlapThreshold }
    }
    val barColor = animateColorAsState(
        if (overlapped) defaultBarColors.scrolledContainerColor else defaultBarColors.containerColor,
        label = "bar colour",
    )
    val readBarColor = remember(barColor) { { barColor.value } }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { if (messages != null) SnackbarHost(messages.hostState) },
        topBar = {
            when (titleSize) {
                TitleSize.Compact -> TopAppBar(
                    title = barTitle,
                    navigationIcon = barNavigation,
                    actions = barActions,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = barColor.value,
                        scrolledContainerColor = barColor.value,
                    ),
                )

                TitleSize.Large -> LargeTopAppBar(
                    title = barTitle,
                    navigationIcon = barNavigation,
                    actions = barActions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = floatingActionButton,
    ) { insets ->
        CompositionLocalProvider(
            LocalBarColor provides readBarColor.takeIf { titleSize == TitleSize.Compact },
        ) {
            content(
                Modifier
                    .fillMaxSize()
                    .padding(insets)
                    // Before the cap and before whatever scroll the body adds. See [imeAware].
                    .then(if (imeAware) Modifier.imePadding() else Modifier)
                    .constrainedContent(),
            )
        }
    }
}

/**
 * The controls a screen pins **under its bar** — «Ученики | Группы», the schedule's view and pager, the
 * report's period — painted as part of the bar.
 *
 * They were page-coloured whatever happened, so the moment a list scrolled the bar turned
 * `surfaceContainer` above a strip that did not: a grey bar, a pale band of controls, then the rows
 * vanishing at an edge nobody had drawn. Four screens, two of them with a hairline under the strip and
 * two without. Now there is one rule, the one [PinnedHeader] already follows on the detail cards: at
 * rest the header is page, no rule under it; once content passes beneath, bar and header tint together
 * and their shared lower edge is the line the rows disappear behind.
 *
 * Only under a compact bar: a large bar collapses, and its screens pin their header inside the list.
 */
@Composable
fun ScreenHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val barColor = LocalBarColor.current
    Column(
        modifier
            .fillMaxWidth()
            .then(if (barColor != null) Modifier.drawBehind { drawRect(barColor()) } else Modifier),
        content = content,
    )
}

/** The compact bar's current colour, read at draw time; null under a large bar. See [ScreenHeader]. */
private val LocalBarColor = staticCompositionLocalOf<(() -> Color)?> { null }

/** How much of the bar content must overlap before it counts as scrolled — Material's own threshold. */
private const val OverlapThreshold = 0.01f

/**
 * «Сохранить», in the app bar — the one way a form commits.
 *
 * Written out four times before this, once per form, and the four agreed only by accident: whether the
 * button is a `TextButton` or a `Button`, and whether it is gated on `canSave` or on field validity, are
 * decisions a tutor should not be able to tell apart between the student form and the booking form.
 * [enabled] is always the form's own `canSave` — see `LeavableForm`.
 */
@Composable
fun SaveAction(enabled: Boolean, onSave: () -> Unit) {
    TextButton(onClick = onSave, enabled = enabled) {
        Text(stringResource(R.string.action_save))
    }
}

/**
 * How a screen's title carries.
 *
 * [Large] is for a screen whose title is its *subject* — a student's name, a group's — where the name
 * is the thing being looked at rather than a label for where you are. It collapses into an ordinary bar
 * as the list scrolls. Everything else is [Compact]: a heading, and it stays put.
 */
enum class TitleSize { Compact, Large }

/**
 * A screen's search box: what is in it, what it is for, and where the typing goes.
 *
 * Deliberately carries no `active` flag. Whether the box is open is [KlasScreen]'s state, because the
 * one rule worth holding — closing it clears the query — is the shell's to enforce, and a screen that
 * owned the flag could close the box without clearing.
 *
 * @param onClose what "cleared" means, defaulting to emptying the box. It is separate from
 *   [onQueryChange] because on People the box is *shared by two lists*: `onQueryChange` writes to
 *   whichever side is on top, so clearing through it would leave the other side's query applied behind
 *   a shut box — a list missing rows for no visible reason, which is the exact thing this rule exists
 *   to prevent. That screen clears both.
 */
class ScreenSearch(
    val query: String,
    val placeholder: String,
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit = { onQueryChange("") },
)

private const val LargeTitleLines = 2

/** How far a bar title may shrink, on screen, before it is cut short — still clearly a title. */
private val MinTitleSize = 18.dp
