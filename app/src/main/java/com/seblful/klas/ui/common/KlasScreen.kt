package com.seblful.klas.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasMotion

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
 * @param maxContentWidth how wide the body may grow: [MaxContentWidth] for a column of rows, more for a body
 * that lays its items out in columns of its own.
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
    maxContentWidth: Dp = MaxContentWidth,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (modifier: Modifier, bottomInset: PaddingValues) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    // The search box, or null when there is none or it is shut — one value rather than a flag plus a
    // nullable, so the two cannot be tested separately and disagree.
    val openSearch = search?.takeIf { searching }
    // `/` on a hardware keyboard opens this search while the screen is on show.
    val searchKey = LocalSearchKey.current
    if (search != null && searchKey != null) {
        DisposableEffect(searchKey) {
            val open = { searching = true }
            searchKey.claim(open)
            onDispose { searchKey.release(open) }
        }
    }

    val scrollBehavior = when (titleSize) {
        TitleSize.Compact -> TopAppBarDefaults.pinnedScrollBehavior()
        TitleSize.Large -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }

    // Only on a *change* of page, not on arriving: a screen returned to restores its list's position and
    // the bar's offset together, and resetting here would leave a scrolled list under an untinted bar.
    // A plain holder, committed after the pass: as state, read and then written here, it recomposed the
    // screen a second time on every change of page.
    val shownPage = remember { ShownPage(page) }
    if (page != shownPage.value) scrollBehavior.state.contentOffset = 0f
    SideEffect { shownPage.value = page }

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
                KlasIcons.Filled.Close,
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
        animationSpec = KlasMotion.defaultEffects(),
        label = "bar colour",
    )
    val readBarColor = remember(barColor) { { barColor.value } }
    // A large bar tints as it collapses, as Material's own does; painted here for the same reason.
    val largeBarColors = TopAppBarDefaults.largeTopAppBarColors()
    val readLargeBarColor = remember(scrollBehavior, largeBarColors) {
        {
            lerp(
                largeBarColors.containerColor,
                largeBarColors.scrolledContainerColor,
                FastOutLinearInEasing.transform(scrollBehavior.state.collapsedFraction),
            )
        }
    }

    // The rail carries a tab's add button in its header, when there is a rail beside it.
    val rail = LocalRailAction.current
    if (rail != null && floatingActionButton != null) OfferToRail(rail, floatingActionButton)
    val frame = remember { ScreenFrame() }
    val medium = mediumWindow()
    val headerPaint = remember(readBarColor, titleSize) {
        BarPaint(readBarColor, frame).takeIf { titleSize == TitleSize.Compact }
    }

    Scaffold(
        modifier = modifier
            .onPlaced { frame.coordinates = it }
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { if (messages != null) SnackbarHost(messages.hostState) },
        topBar = {
            // The tint spans the window; the bar's title and actions line up over the capped column, and
            // its sides keep clear of a camera cutout in landscape.
            val paint = if (titleSize == TitleSize.Compact) readBarColor else readLargeBarColor
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(paint()) }
                    .windowInsetsPadding(BarInsets.only(WindowInsetsSides.Horizontal)),
            ) {
                val barModifier = Modifier.fillMaxWidth().windowMargins(medium).constrainedContent(maxContentWidth)
                val transparent = Color.Transparent
                when (titleSize) {
                    // Painted behind a see-through bar, from the draw phase: given as the bar's colour, the
                    // tint recomposed the whole bar on every frame of its fade.
                    TitleSize.Compact -> TopAppBar(
                        title = barTitle,
                        modifier = barModifier,
                        navigationIcon = barNavigation,
                        actions = barActions,
                        windowInsets = BarInsets.only(WindowInsetsSides.Top),
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = transparent,
                            scrolledContainerColor = transparent,
                        ),
                    )

                    TitleSize.Large -> LargeTopAppBar(
                        title = barTitle,
                        modifier = barModifier,
                        navigationIcon = barNavigation,
                        actions = barActions,
                        expandedHeight = LargeTitleBarHeight,
                        windowInsets = BarInsets.only(WindowInsetsSides.Top),
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = transparent,
                            scrolledContainerColor = transparent,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (rail == null && floatingActionButton != null) {
                Box(Modifier.besideColumn(maxContentWidth, medium)) { floatingActionButton() }
            }
        },
        contentWindowInsets = ContentInsets,
    ) { insets ->
        val density = LocalDensity.current
        val ime = WindowInsets.ime
        val aboveBottom = remember(insets) { AboveBottom(insets) }
        val bottomInset = remember(insets, imeAware, density) {
            BottomInset(insets, keyboard = ime.takeIf { imeAware }, density)
        }
        CompositionLocalProvider(
            LocalBarPaint provides headerPaint,
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
                    .windowMargins(medium)
                    .constrainedContent(maxContentWidth),
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
    val paint = LocalBarPaint.current
    val own = remember { ScreenFrame() }
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (paint != null) {
                    Modifier.onPlaced { own.coordinates = it }.drawBehind { with(paint) { under(own) } }
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/** The bar's tint and the screen it spans, for a [ScreenHeader] narrower than its screen to paint as wide. */
private class BarPaint(val color: () -> Color, val screen: ScreenFrame) {
    /** Edge to edge of the screen, as the bar is tinted: a capped column's header is no narrower than its bar. */
    fun DrawScope.under(header: ScreenFrame) {
        val screenCoordinates = screen.coordinates?.takeIf { it.isAttached }
        val headerCoordinates = header.coordinates?.takeIf { it.isAttached }
        if (screenCoordinates == null || headerCoordinates == null) {
            drawRect(color())
            return
        }
        val left = screenCoordinates.localPositionOf(headerCoordinates, Offset.Zero).x
        drawRect(color(), topLeft = Offset(-left, 0f), size = Size(screenCoordinates.size.width.toFloat(), size.height))
    }
}

/** Where a layout was last placed — kept out of snapshot state, as it is only read to draw. */
private class ScreenFrame(var coordinates: LayoutCoordinates? = null)

private val LocalBarPaint = staticCompositionLocalOf<BarPaint?> { null }

/** The page [KlasScreen] last drew, kept out of snapshot state — see its `page`. */
private class ShownPage(var value: Any?)

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

/** The system bars and, in landscape, a camera cutout: what a screen keeps its content clear of. */
private val ContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/** The bar's own insets, with the cutout it would otherwise draw its back arrow under. */
private val BarInsets: WindowInsets
    @Composable get() = TopAppBarDefaults.windowInsets.union(WindowInsets.displayCutout)

/**
 * Material's margins: 16 dp on a compact window, 24 dp from [medium] on — by the window's class, so a pane or
 * a body beside the rail keeps them too. The body's own gutter is the 16, so it is narrowed by the difference.
 */
internal fun Modifier.windowMargins(medium: Boolean): Modifier = layout { measurable, constraints ->
    val extra = if (medium && constraints.hasBoundedWidth) (MediumMargin - Spacing.gutter).roundToPx() else 0
    val placeable = measurable.measure(constraints.offset(horizontal = -2 * extra))
    layout(placeable.width + 2 * extra, placeable.height) { placeable.placeRelative(extra, 0) }
}

/**
 * The FAB at the end of the capped column rather than in the window's corner. Scaffold measures it against
 * the whole window, so the room either side of the column is known here, where it is laid out.
 */
private fun Modifier.besideColumn(cap: Dp, medium: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val width = constraints.maxWidth
    val margin = if (medium) (MediumMargin - Spacing.gutter).roundToPx() else 0
    val beside = maxOf((width - cap.roundToPx()) / 2, margin).coerceAtLeast(0)
    layout(placeable.width + beside, placeable.height) { placeable.placeRelative(0, 0) }
}

private val MediumMargin = 24.dp

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

/**
 * Shorter than Material's 152 dp, so the name sits just under the arrow with the facts under it, not a band
 * of empty bar between. A two-line name steps its size down to fit (see the title's autosize) rather than clip.
 */
private val LargeTitleBarHeight = 128.dp

private val MinTitleSize = 18.dp
