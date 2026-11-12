package com.seblful.klas.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.seblful.klas.ui.theme.ContrastLevel
import com.seblful.klas.ui.theme.klasColorScheme
import com.seblful.klas.ui.theme.klasColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgeContrastTest {

    @Test
    fun `a status pill's words read on it over every card surface`() {
        val failures = listOf(false, true).flatMap { dark ->
            ContrastLevel.entries.flatMap { level ->
                val scheme = klasColorScheme(dark, level)
                val custom = klasColors(dark, level)
                val tints = mapOf(
                    "primary" to scheme.primary,
                    "error" to scheme.error,
                    "onSurfaceVariant" to scheme.onSurfaceVariant,
                    "success" to custom.success,
                    "live" to custom.live,
                )
                val surfaces = listOf(scheme.surface, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
                tints.flatMap { (name, tint) ->
                    // The badge judges itself on the highest surface; it must hold on every lower one too.
                    val worst = tint.copy(alpha = 0.15f).compositeOver(scheme.surfaceContainerHighest)
                    val text = readableOn(worst, tint, dark)
                    surfaces.mapNotNull { surface ->
                        val pill = tint.copy(alpha = 0.15f).compositeOver(surface)
                        val ratio = contrastRatio(text, pill)
                        "$dark $level $name on $surface: $ratio".takeIf { ratio < 4.5f }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `a colour that already reads is left as it is`() {
        assertEquals(Color.Black, readableOn(Color.White, Color.Black, dark = false))
    }
}
