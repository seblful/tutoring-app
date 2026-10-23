package com.seblful.klas.preview

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.ui.common.LocalCurrency
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
import com.seblful.klas.ui.theme.ContrastLevel
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasTheme

/*
 * The component catalog: every design-system part, in each of its states, on one page — the place to look
 * before building something new. Each preview below is a screenshot target (KlasPreviewTester), so a part
 * that changes shows up here in light, dark, 1.5× text and both raised contrast schemes.
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        ButtonKind.entries.forEach { kind -> KlasButton(onClick = {}, kind = kind) { Text(kind.name) } }
        KlasButton(onClick = {}, enabled = false) { Text("Disabled") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        DoneButton(done = false, label = "Mark taught", doneWord = "Taught", onDone = {}, onOpen = {})
        DoneButton(done = true, label = "Mark taught", doneWord = "Taught", onDone = {}, onOpen = {})
        DoneButton(done = false, label = "Mark taught", doneWord = "Taught", onDone = {}, onOpen = {}, enabled = false)
    }
    KlasFab("Book", onClick = {})
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

@KlasPreview
@Composable
private fun ComponentCatalogPreview() = CatalogSurface(ContrastLevel.Standard, dark = isSystemInDarkTheme())

@Preview(name = "Medium contrast", group = "catalog", showBackground = true, widthDp = 360)
@Composable
private fun ComponentCatalogMediumPreview() = CatalogSurface(ContrastLevel.Medium, dark = false)

@Preview(name = "High contrast", group = "catalog", showBackground = true, widthDp = 360)
@Composable
private fun ComponentCatalogHighPreview() = CatalogSurface(ContrastLevel.High, dark = false)

@Preview(
    name = "High contrast, dark",
    group = "catalog",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComponentCatalogHighDarkPreview() = CatalogSurface(ContrastLevel.High, dark = true)

/** The catalog on the app's theme at [contrast]; [dark] for the night scheme. */
@Composable
private fun CatalogSurface(contrast: ContrastLevel, dark: Boolean) {
    KlasTheme(darkTheme = dark, contrast = contrast) {
        CompositionLocalProvider(LocalCurrency provides Currency.BYN) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(Spacing.gutter)) { ComponentCatalog() }
            }
        }
    }
}
