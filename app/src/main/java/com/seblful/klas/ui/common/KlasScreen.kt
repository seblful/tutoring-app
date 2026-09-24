package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
                // A large title has two lines to spend on a name; a compact one shares its row with a
                // back arrow and up to three actions, and must give way rather than push them off.
                maxLines = if (titleSize == TitleSize.Large) LargeTitleLines else 1,
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
