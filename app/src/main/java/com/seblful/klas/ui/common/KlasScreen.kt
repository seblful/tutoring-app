package com.seblful.klas.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasIcons

/**
 * Every screen in the app, from the app bar down.
 *
 * @param title the bar's text. `null` for a screen whose body names itself (the welcome step, a detail
 * card that has not read its subject yet) — inventing a placeholder is the one thing a loading bar
 * must not do.
 * @param onBack the back arrow, or `null` on a tab. A form passes its `leaveGuard`, not `onBack`.
 * @param search when non-null, the bar grows a search icon and becomes a search field when it is
 * tapped. The toggle is state, and it is *this* function's.
 * @param messages the screen's [Messages]; its host is mounted here so no screen mounts its own.
 * @param page which page of the screen is up — a tab of «Люди», a view of the schedule — for a screen
 * whose body is swapped for another *without scrolling*. The bar keeps count of how far content has
 * scrolled beneath it, and a page that opens at its top has scrolled nowhere: without this, «Группы»
 * opened under a bar (and a [ScreenHeader]) still tinted for the students list scrolled a moment ago.
 * @param content given the modifier for the screen's top-level scrollable — the top and side insets and the
 * cap applied — and the **bottom inset**, which the body owns: a list adds it to its `contentPadding`
 * (see [plusInset]) and a scrolling column pads by it *inside* its scroll, so rows pass under the system
 * navigation bar and the last one still clears it. A body that does not scroll pads by it. It reads as
 * nothing on a tab, where the tab bar has taken the inset, and on an [imeAware] form while the keyboard
 * covers it.
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
    content: @Composable (modifier: Modifier, bottomInset: PaddingValues) -> Unit,
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
            NamedIconButton(KlasIcons.Filled.ArrowBack, stringResource(R.string.action_back), onClick = onBack)
        }
    }

    val barActions: @Composable RowScope.() -> Unit = {
        if (openSearch != null) {
            // Closing search clears it. The screen's own actions stay hidden while the box is open —
            // the bar is one row, and a search field plus three icons is not a row.
            NamedIconButton(
                KlasIcons.Filled.SearchOff,
                stringResource(R.string.search_close),
                onClick = { searching = false; openSearch.onClose() },
            )
        } else {
            if (search != null) {
                NamedIconButton(
                    KlasIcons.Filled.Search,
                    stringResource(R.string.action_search),
                    onClick = { searching = true },
                )
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
        val density = LocalDensity.current
        val ime = WindowInsets.ime
        val aboveBottom = remember(insets) { AboveBottom(insets) }
        val bottomInset = remember(insets, imeAware, density) {
            BottomInset(insets, keyboard = ime.takeIf { imeAware }, density)
        }
        CompositionLocalProvider(
            LocalBarColor provides readBarColor.takeIf { titleSize == TitleSize.Compact },
        ) {
            content(
                Modifier
                    .fillMaxSize()
                    .padding(aboveBottom)
                    // Only what was padded: the navigation bar's inset is still the keyboard's to cover, so
                    // the IME padding below reaches the keyboard's top rather than a bar's height short of it.
                    .consumeWindowInsets(aboveBottom)
                    // Before the cap and before whatever scroll the body adds. See [imeAware].
                    .then(if (imeAware) Modifier.imePadding() else Modifier)
                    // The bottom is the body's now (`bottomInset`); nothing under it pads by it again.
                    .consumeWindowInsets(insets)
                    .constrainedContent(),
                bottomInset,
            )
        }
    }
}

/**
 * The controls a screen pins **under its bar** — «Ученики | Группы», the schedule's view and pager, the
 * report's period — painted as part of the bar.
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

private val LocalBarColor = staticCompositionLocalOf<(() -> Color)?> { null }

/** The Scaffold's insets without the bottom one, which [KlasScreen] hands its body instead. */
private class AboveBottom(private val insets: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = insets.calculateLeftPadding(layoutDirection)
    override fun calculateTopPadding() = insets.calculateTopPadding()
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = insets.calculateRightPadding(layoutDirection)
    override fun calculateBottomPadding() = 0.dp
}

/**
 * The Scaffold's bottom inset, less what of it the [keyboard] covers on a form that pads for one. Read at
 * layout, like the Scaffold's own, so it follows the keyboard's slide without recomposing the body.
 */
private class BottomInset(
    private val insets: PaddingValues,
    private val keyboard: WindowInsets?,
    private val density: Density,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
    override fun calculateTopPadding() = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
    override fun calculateBottomPadding(): Dp {
        val covered = keyboard?.let { with(density) { it.getBottom(this).toDp() } } ?: 0.dp
        return (insets.calculateBottomPadding() - covered).coerceAtLeast(0.dp)
    }
}

/** A scrollable's own padding with [inset] — [KlasScreen]'s bottom inset — added to it. */
fun PaddingValues.plusInset(inset: PaddingValues): PaddingValues = SumOfPaddings(this, inset)

private class SumOfPaddings(private val first: PaddingValues, private val second: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        first.calculateLeftPadding(layoutDirection) + second.calculateLeftPadding(layoutDirection)
    override fun calculateTopPadding() = first.calculateTopPadding() + second.calculateTopPadding()
    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        first.calculateRightPadding(layoutDirection) + second.calculateRightPadding(layoutDirection)
    override fun calculateBottomPadding() = first.calculateBottomPadding() + second.calculateBottomPadding()
}

private const val OverlapThreshold = 0.01f

/** Сохранить», in the app bar — the one way a form commits. */
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
 * @param onClose what "cleared" means, defaulting to emptying the box. It is separate from
 * [onQueryChange] because on People the box is *shared by two lists*: `onQueryChange` writes to
 * whichever side is on top, so clearing through it would leave the other side's query applied behind
 * a shut box — a list missing rows for no visible reason, which is the exact thing this rule exists
 * to prevent. That screen clears both.
 */
class ScreenSearch(
    val query: String,
    val placeholder: String,
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit = { onQueryChange("") },
)

private const val LargeTitleLines = 2

private val MinTitleSize = 18.dp
