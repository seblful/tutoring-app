package com.seblful.klas.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.designsystem.component.AccentCard
import com.seblful.klas.ui.designsystem.component.Amount
import com.seblful.klas.ui.designsystem.component.AmountMeaning
import com.seblful.klas.ui.designsystem.component.AmountText
import com.seblful.klas.ui.designsystem.component.Banner
import com.seblful.klas.ui.designsystem.component.BannerKind
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.DoneButton
import com.seblful.klas.ui.designsystem.component.FilterBar
import com.seblful.klas.ui.designsystem.component.FilterOption
import com.seblful.klas.ui.designsystem.component.FormBar
import com.seblful.klas.ui.designsystem.component.FormSaveBar
import com.seblful.klas.ui.designsystem.component.HeaderedCard
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.designsystem.component.KlasFab
import com.seblful.klas.ui.designsystem.component.KlasRow
import com.seblful.klas.ui.designsystem.component.MenuChip
import com.seblful.klas.ui.designsystem.component.RowEnd
import com.seblful.klas.ui.designsystem.component.RowStart
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import com.seblful.klas.ui.designsystem.component.SliceHeader
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.designsystem.component.SurfaceRow
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The component catalog: every design-system part, in each of its states, on one page — the place to look
 * before building something new. Its previews (ComponentCatalogPreviews.kt) are screenshot targets, so a
 * part that changes shows up in light, dark, 1.5× text and both raised contrast schemes.
 */

/** The catalog, in sections: actions, rows, cards and banners, and the form bar. */
@Composable
internal fun ComponentCatalog() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)) {
        CatalogSection("Actions") { Actions() }
        CatalogSection("Rows") { Rows() }
        CatalogSection("Cards and banners") { CardsAndBanners() }
        CatalogSection("Form bar") { FormBars() }
    }
}

@Composable
private fun CatalogSection(name: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Actions() {
    // Grouped by shape: a text button keeps Material's inner padding for its touch target, so beside pills
    // its word sits inset and the row looks ragged.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        verticalArrangement = Arrangement.spacedBy(Spacing.inline),
    ) {
        ButtonKind.entries.filter { it !in TextKinds }.forEach { kind ->
            KlasButton(onClick = {}, kind = kind) { Text(kind.words) }
        }
        KlasButton(onClick = {}, enabled = false) { Text("Disabled") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        TextKinds.forEach { kind -> KlasButton(onClick = {}, kind = kind) { Text(kind.words) } }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sectionGap)) {
        Captioned("To mark") {
            DoneButton(done = false, label = "Mark taught", doneWord = "Taught", onDone = {}, onOpen = {})
        }
        Captioned("Taught") {
            DoneButton(done = true, label = "Mark taught", doneWord = "Taught", onDone = {}, onOpen = {})
        }
        Captioned("Disabled") {
            DoneButton(
                done = false, label = "Mark taught", doneWord = "Taught", onDone = {}, onOpen = {}, enabled = false,
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sectionGap)) {
        // At 1.5× text the expanded one folds to its icon too: the label would not fit beside a long list.
        Captioned("Expanded") { KlasFab("Book", onClick = {}) }
        Captioned("Scrolled") { KlasFab("Book", onClick = {}, expanded = false) }
    }
    FilterBar(
        listOf(FilterOption("Owes · 3", selected = true, onClick = {}), FilterOption("Archived", false, {})),
    ) {
        MenuChip(label = "Sort: Name", narrowed = false) { close ->
            TickedMenuItem("Name", selected = true, onClick = close)
        }
    }
}

@Composable
private fun Rows() {
    Box(Modifier.intoGutter()) { RowsGroup() }
    Column {
        AmountMeaning.entries.forEachIndexed { index, meaning ->
            SurfaceRow(SlicePosition.of(index, AmountMeaning.entries.size), onClick = {}) {
                KlasRow(meaning.name, end = RowEnd.Money(AmountText("200 BYN", meaning)))
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        Amount(AmountText("Owes 200 BYN", AmountMeaning.Debt))
        Amount(AmountText("80 BYN in credit", AmountMeaning.Credit))
    }
}

/** The rows on a settings card — which brings its own gutter, being laid on a page rather than in one. */
@Composable
private fun RowsGroup() {
    SettingsGroup {
        KlasRow("Plain row", supporting = "Its supporting line")
        KlasRow("Opens a page", start = RowStart.Icon(KlasIcons.Outlined.Info), end = RowEnd.Opens, onClick = {})
        KlasRow("A value", end = RowEnd.Value("1 hour before"), onClick = {})
        KlasRow("A switch", end = RowEnd.Toggle(on = true) {})
        KlasRow("One action", end = RowEnd.Action(KlasIcons.Filled.Undo, "Undo") {})
        KlasRow(
            "Liam Carter",
            supporting = "16:00–17:00 · Physics",
            start = RowStart.Done(done = false, label = "Mark taught", doneWord = "Taught", onDone = {}, onOpen = {}),
            end = RowEnd.Opens,
            onClick = {},
        )
        KlasRow("Disabled", supporting = "Says how to enable it", end = RowEnd.Toggle(false) {}, enabled = false)
    }
}

/** Out into the page's gutter on both sides, for a part that adds its own. */
private fun Modifier.intoGutter() = layout { measurable, constraints ->
    val gutter = Spacing.gutter.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = 2 * gutter))
    layout(placeable.width - 2 * gutter, placeable.height) { placeable.place(-gutter, 0) }
}

@Composable
private fun CardsAndBanners() {
    AccentCard(onClick = {}) { Text("Next · in 55 min", Modifier.padding(Spacing.gutter)) }
    Column {
        SliceHeader(
            "Thu 22 Oct", SlicePosition.FIRST, summary = "2 lessons · 2 h",
            action = SectionAction("Cancel day") {
            },
        )
        SurfaceRow(SlicePosition.LAST, onClick = {}) { KlasRow("16:00–17:00 Liam Carter", end = RowEnd.Opens) }
    }
    HeaderedCard("Balance", summary = "Last payment 18 Sep", action = SectionAction("Statement") {}) {
        KlasRow("Owes 200 BYN")
        KlasDivider(Modifier.padding(horizontal = Spacing.gutter))
        KlasRow("Earned 862.50 BYN · 34.5 h")
    }
    Banner("Notifications are blocked", BannerKind.Blocking, action = SectionAction("Allow") {})
    Banner(
        "No backup yet",
        BannerKind.Advisory,
        detail = "Your data is on this phone only.",
        action = SectionAction("Back up") {},
    )
}

@Composable
private fun FormBars() {
    val none = PaddingValues()
    FormSaveBar("Add student", FormBar.Missing(listOf("a name", "a rate")), onSave = {}, none)
    FormSaveBar("Book", FormBar.Blocked("16:00 is Olivia Brooks's lesson"), onSave = {}, none)
    FormSaveBar("Add student", FormBar.Ready("Sofia Martin", "Maths 25 BYN/h · In person"), onSave = {}, none)
}

/** A part's state named under it, where the part alone does not say which state it is in. */
@Composable
private fun Captioned(caption: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The kinds drawn as a word alone, with no container. */
private val TextKinds = listOf(ButtonKind.Text, ButtonKind.Quiet, ButtonKind.Destructive)

/** «DestructiveOutlined» as «Destructive outlined». */
private val ButtonKind.words
    get() = name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar(Char::uppercase)
