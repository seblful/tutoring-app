package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A typed amount is a [MoneyTextField], and a dialog's survives** (docs/ui-conventions.md → Screen state).
 *
 * Seven money inputs spelled the field out by hand, and three of the dialogs among them held their
 * `MoneyField` in a plain `remember` — so the half-typed rate in «Изменить цену», the payment being
 * entered and the default group price were lost whenever the dialog was recreated, while every other typed
 * dialog field in the app is `rememberSaveable`. The field is one composable now; a dialog keeps the
 * *text* saveable and derives the field from it.
 */
class MoneyFieldSurfaceTest {

    private fun uiSources() = ProjectFiles.sources().filter { "ui" in it.path.split(java.io.File.separator) }

    @Test
    fun `no screen spells a money field out by hand`() {
        val offenders = uiSources()
            .filterNot { it.name == "Fields.kt" }
            .filter { file ->
                SourceText.argumentLists(SourceText.code(file.readText()), "TextField(").any { args ->
                    "moneyErrorText(" in args
                }
            }
            .map { it.name }
        assertEquals(
            "A money input is an `OutlinedTextField` with its error spelled out again, instead of " +
                "`MoneyTextField` (ui/common/Fields.kt): $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no money field is held in a plain remember`() {
        val remembered = Regex("""remember\s*\{\s*mutableStateOf\(\s*MoneyField""")
        val offenders = uiSources()
            .filter { remembered.containsMatchIn(SourceText.code(it.readText())) }
            .map { it.name }
        assertEquals(
            "A `MoneyField` lives in a plain `remember`, so what was typed is lost when the dialog is " +
                "recreated. Keep its text in `rememberSaveable` and derive the field: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /** And the forms and dialogs do type amounts through it, so the scans above have something to scan. */
    @Test
    fun `the forms and the money dialogs use it`() {
        val users = uiSources()
            .filter { SourceText.code(it.readText()).contains("MoneyTextField(") }
            .map { it.name }
            .toSet()
        assertTrue(
            "Expected the booking, group, student forms, the student card and settings to use " +
                "`MoneyTextField`: $users",
            users.containsAll(
                setOf(
                    "ClassEditScreen.kt",
                    "GroupFormScreen.kt",
                    "StudentFormScreen.kt",
                    "StudentDetailScreen.kt",
                    "SettingsScreen.kt",
                ),
            ),
        )
    }
}
