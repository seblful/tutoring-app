package com.seblful.klas.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
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
import com.seblful.klas.ui.theme.KlasTheme

/**
 * Shared component previews use Russian, light and dark themes, and 1.5× text. Screenshot tests cover
 * every preview; see `docs/testing.md`.
 */
@Preview(name = "Light", group = "component", showBackground = true, widthDp = 360, locale = "ru")
@Preview(
    name = "Dark",
    group = "component",
    showBackground = true,
    widthDp = 360,
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Large text",
    group = "component",
    showBackground = true,
    widthDp = 360,
    locale = "ru",
    fontScale = 1.5f,
)
annotation class KlasPreview

/**
 * Provides the app theme and currency to component previews.
 */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    KlasTheme {
        CompositionLocalProvider(LocalCurrency provides Currency.BYN) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                ) {
                    content()
                }
            }
        }
    }
}
