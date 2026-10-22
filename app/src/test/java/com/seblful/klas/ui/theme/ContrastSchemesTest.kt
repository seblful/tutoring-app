package com.seblful.klas.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastSchemesTest {

    private val levels = ContrastLevel.entries
    private val themes = listOf(false, true)

    @Test
    fun `text roles reach each contrast level's minimum on their containers`() {
        val failures = themes.flatMap { dark ->
            levels.flatMap { level ->
                val scheme = klasColorScheme(dark, level)
                textPairs(scheme).mapNotNull { (name, text, background) ->
                    val ratio = contrast(text, background)
                    "${if (dark) "dark" else "light"} $level $name: $ratio".takeIf { ratio < minimum(level) }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the app's own status colours read on every card surface at every level`() {
        val failures = themes.flatMap { dark ->
            levels.flatMap { level ->
                val scheme = klasColorScheme(dark, level)
                val colors = klasColors(dark, level)
                listOf(scheme.surface, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest).flatMap { surface ->
                    listOf(
                        "success" to contrast(colors.success, surface),
                        "live" to contrast(colors.live, surface),
                    ).filter { it.second < WcagAaText }.map { "$dark $level ${it.first}: ${it.second}" }
                } + listOf("onSuccessContainer" to contrast(colors.onSuccessContainer, colors.successContainer))
                    .filter { it.second < minimum(level) }.map { "$dark $level ${it.first}: ${it.second}" }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `every meaning reads as text on every card surface, and its banners on their containers`() {
        val failures = themes.flatMap { dark ->
            levels.flatMap { level ->
                val scheme = klasColorScheme(dark, level)
                val roles = KlasSemantics.of(scheme)
                val words = listOf(
                    "debt" to roles.debt,
                    "overdue" to roles.overdue,
                    "destructive" to roles.destructive,
                    "validation" to roles.validation,
                    "cancelled" to roles.cancelled,
                    "attention" to roles.attention,
                )
                listOf(scheme.surface, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest).flatMap { surface ->
                    words.map { (name, color) -> name to contrast(color, surface) }
                        .filter { it.second < WcagAaText }.map { "$dark $level ${it.first}: ${it.second}" }
                } + listOf(
                    "blocking" to contrast(roles.onBlocking, roles.blocking),
                    "advisory" to contrast(roles.onAdvisory, roles.advisory),
                ).filter { it.second < minimum(level) }.map { "$dark $level ${it.first}: ${it.second}" }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `advisory never looks like on-now or taught`() {
        themes.forEach { dark ->
            val roles = KlasSemantics.of(klasColorScheme(dark, ContrastLevel.Standard))
            val colors = klasColors(dark, ContrastLevel.Standard)
            assertNotEquals(colors.live, roles.advisory)
            assertNotEquals(colors.successContainer, roles.advisory)
        }
    }

    @Test
    fun `each level darkens the page's text against it`() {
        themes.forEach { dark ->
            val ratios = levels.map { level ->
                klasColorScheme(dark, level).let { contrast(it.onSurfaceVariant, it.surface) }
            }
            assertEquals("$dark $ratios", ratios.sorted(), ratios)
            assertTrue("$dark $ratios", ratios.distinct().size == ratios.size)
        }
    }

    @Test
    fun `no role of the standard schemes falls back to Material's baseline purple`() {
        val baseline = lightColorScheme()
        themes.forEach { dark ->
            val scheme = klasColorScheme(dark, ContrastLevel.Standard)
            assertNotEquals(baseline.inversePrimary, scheme.inversePrimary)
            assertNotEquals(baseline.inverseSurface, scheme.inverseSurface)
            assertNotEquals(baseline.primaryFixed, scheme.primaryFixed)
            assertNotEquals(baseline.tertiaryFixedDim, scheme.tertiaryFixedDim)
        }
    }

    @Test
    fun `fixed accents are the same in both themes`() {
        val light = klasColorScheme(darkTheme = false, ContrastLevel.Standard)
        val dark = klasColorScheme(darkTheme = true, ContrastLevel.Standard)
        assertEquals(light.primaryFixed, dark.primaryFixed)
        assertEquals(light.onSecondaryFixedVariant, dark.onSecondaryFixedVariant)
        assertEquals(light.tertiaryFixedDim, dark.tertiaryFixedDim)
    }

    @Test
    fun `the phone's contrast setting maps onto Material's three levels`() {
        assertEquals(ContrastLevel.Standard, ContrastLevel.of(0f))
        assertEquals(ContrastLevel.Standard, ContrastLevel.of(-1f))
        assertEquals(ContrastLevel.Medium, ContrastLevel.of(0.5f))
        assertEquals(ContrastLevel.High, ContrastLevel.of(1f))
    }

    private fun textPairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onPrimary", s.onPrimary, s.primary),
        Triple("onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondary", s.onSecondary, s.secondary),
        Triple("onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiary", s.onTertiary, s.tertiary),
        Triple("onTertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onError", s.onError, s.error),
        Triple("onErrorContainer", s.onErrorContainer, s.errorContainer),
        Triple("onSurface", s.onSurface, s.surface),
        Triple("onSurface on highest", s.onSurface, s.surfaceContainerHighest),
        Triple("onSurfaceVariant", s.onSurfaceVariant, s.surface),
        Triple("onSurfaceVariant on highest", s.onSurfaceVariant, s.surfaceContainerHighest),
        Triple("primary", s.primary, s.surface),
        Triple("primary on highest", s.primary, s.surfaceContainerHighest),
        Triple("error", s.error, s.surface),
        Triple("inverseOnSurface", s.inverseOnSurface, s.inverseSurface),
        Triple("inversePrimary", s.inversePrimary, s.inverseSurface),
        Triple("onPrimaryFixed", s.onPrimaryFixed, s.primaryFixed),
        Triple("onSecondaryFixed", s.onSecondaryFixed, s.secondaryFixed),
        Triple("onTertiaryFixed", s.onTertiaryFixed, s.tertiaryFixed),
    )

    // Material's medium level buys saturated containers at the price of their text, which stays at AA.
    private fun minimum(level: ContrastLevel): Float = when (level) {
        ContrastLevel.Standard, ContrastLevel.Medium -> WcagAaText
        ContrastLevel.High -> WcagAaaText
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private companion object {
        const val WcagAaText = 4.5f
        const val WcagAaaText = 7f
    }
}
