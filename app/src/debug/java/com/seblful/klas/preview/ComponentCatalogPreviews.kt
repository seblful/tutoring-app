package com.seblful.klas.preview

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.ContrastLevel
import com.seblful.klas.ui.theme.KlasTheme

// Taller than [KlasPreview]'s screen, so each screenshot holds the whole catalog rather than its first part.
@Preview(name = "Light", group = "catalog", showBackground = true, widthDp = 360, heightDp = CatalogHeight)
@Preview(
    name = "Dark",
    group = "catalog",
    showBackground = true,
    widthDp = 360,
    heightDp = CatalogHeight,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Large text",
    group = "catalog",
    showBackground = true,
    widthDp = 360,
    heightDp = CatalogLargeTextHeight,
    fontScale = 1.5f,
)
@Composable
private fun ComponentCatalogPreview() = CatalogSurface(ContrastLevel.Standard, dark = isSystemInDarkTheme())

@Preview(name = "Medium contrast", group = "catalog", showBackground = true, widthDp = 360, heightDp = CatalogHeight)
@Composable
private fun ComponentCatalogMediumPreview() = CatalogSurface(ContrastLevel.Medium, dark = false)

@Preview(name = "High contrast", group = "catalog", showBackground = true, widthDp = 360, heightDp = CatalogHeight)
@Composable
private fun ComponentCatalogHighPreview() = CatalogSurface(ContrastLevel.High, dark = false)

@Preview(
    name = "High contrast, dark",
    group = "catalog",
    showBackground = true,
    widthDp = 360,
    heightDp = CatalogHeight,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComponentCatalogHighDarkPreview() = CatalogSurface(ContrastLevel.High, dark = true)

private const val CatalogHeight = 2000
private const val CatalogLargeTextHeight = 2900

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
