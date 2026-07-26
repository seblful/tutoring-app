package com.seblful.klas.data.local

import com.seblful.klas.testutil.ProjectFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The one promise that keeps a released install's data: **a bumped schema version ships a migration.**
 *
 * The database is at v1 with no migrations, which is honest today — `com.seblful.klas` has never been
 * installed anywhere, so no device holds a `klas.db` to carry forward (see [AppDatabase.NAME]). The
 * moment v1 is released that stops being true, and the mistake it opens up is the quietest one in the
 * project: bump `version`, forget the migration, and Room finds no path from the version on the
 * tutor's phone. A debug build then hands it to the destructive fallback and a release build refuses
 * to open at all (`di/DatabaseModule`) — neither of which is an upgrade, and the first of which is
 * indistinguishable from the app having lost every student, lesson and payment.
 *
 * Nothing else in the build says so. The instrumented migration tests are the real proof that a
 * migration *works*, but they need a device and CI only compiles them, so a missing migration reaches
 * a phone before anything complains. These assertions are cheap enough to run in the unit suite, which
 * means they run on every push — they check that the chain *is there and is wired*, not that each step
 * is correct.
 *
 * Like [StoredEnumNamesTest], this reads source text, and for the same kind of reason: `@Database` is
 * `BINARY`-retained, so the version the app declares cannot be read back at runtime. The exported
 * schema JSON is a build product of that declaration, so it is checked *against* it rather than
 * trusted as it — a stale export outlives the bump that produced it.
 */
class MigrationChainTest {

    @Test
    fun `the exported schemas are exactly the versions up to the one the app declares`() {
        assertEquals(
            "The exported schema JSONs and the version AppDatabase declares (v$declaredVersion) " +
                "disagree. A migration is written and validated against an exported schema, so a " +
                "missing one cannot be tested, and a leftover one describes a version the app no " +
                "longer has (delete it, or restore the bump it belongs to).",
            (1..declaredVersion).toList(),
            exportedVersions,
        )
        val exported = Json.parseToJsonElement(schemaFile(declaredVersion).readText())
            .jsonObject.getValue("database").jsonObject
            .getValue("version").jsonPrimitive.int
        assertEquals(
            "The newest exported schema is not the one the app declares — it is stale. Build the " +
                "debug variant to regenerate it (KSP writes it), and commit the result.",
            declaredVersion,
            exported,
        )
    }

    @Test
    fun `a bumped schema version ships an unbroken chain of migrations`() {
        assertEquals(
            "The schema is at v$declaredVersion, so Room needs a migration for every single step " +
                "from v1 up to it — a gap is a released database with no upgrade path, and an extra " +
                "step migrates to a version that does not exist. Write the missing migration in " +
                "data/local/Migrations.kt (it is absent entirely while the schema is at v1).",
            (1 until declaredVersion).map { it to it + 1 },
            migrationSteps(),
        )
    }

    @Test
    fun `the migrations are wired into the database that ships`() {
        assertEquals(
            "Migrations.kt is not what Room reads — DatabaseModule's builder is. A chain that exists " +
                "but is never passed to `addMigrations` leaves every upgrade with no path at all, " +
                "which is exactly the state having written the migration is supposed to rule out.",
            declaredVersion > 1,
            databaseModuleSource.contains("addMigrations"),
        )
    }

    /**
     * The destructive fallback recreates the database empty. It is meant for a dev device carrying a
     * pre-release schema, so it is gated on the debug build — and that gate is the difference between
     * a forgotten migration costing a crash and it costing a tutor's entire history. It is also the
     * obvious thing to delete when a release build starts throwing, which is why it is asserted here
     * rather than left to review.
     */
    @Test
    fun `a release build has no destructive path`() {
        if (!databaseModuleSource.contains("fallbackToDestructiveMigration")) return
        assertEquals(
            "DatabaseModule calls fallbackToDestructiveMigration without an `if (debugBuild)` guard, " +
                "so a release build would answer a missing migration by dropping every table. Keep " +
                "the guard (rename it here if the flag is now called something else) — a release " +
                "build must fail to open a database it cannot migrate, leaving the rows on disk for " +
                "the next build to migrate properly.",
            true,
            databaseModuleSource.contains("if (debugBuild)"),
        )
    }

    // ---- the declaration, and what the build made of it ------------------------------------------

    /** The version `@Database` declares today. */
    private val declaredVersion: Int by lazy {
        val declarations = Regex("""\bversion\s*=\s*(\d+)""")
            .findAll(ProjectFiles.sourceFile("data/local/AppDatabase.kt").readText())
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            "AppDatabase.kt should state its schema version exactly once; found $declarations. " +
                "This test reads it from the source because @Database is BINARY-retained, so if the " +
                "declaration moved or gained a lookalike, point this test at the real one.",
            1,
            declarations.size,
        )
        declarations.single().toInt()
    }

    /** Every version Room has exported a schema for, ascending. */
    private val exportedVersions: List<Int> by lazy { schemaFiles.keys.sorted() }

    /**
     * The `(from, to)` step of every migration the project declares, ascending — empty while
     * `Migrations.kt` does not exist, which is the state a v1 schema is supposed to be in.
     */
    private fun migrationSteps(): List<Pair<Int, Int>> {
        val source = ProjectFiles.sourceFileOrNull("data/local/Migrations.kt") ?: return emptyList()
        return Regex("""Migration\((\d+)\s*,\s*(\d+)\)""")
            .findAll(source.readText())
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .sortedBy { it.first }
            .toList()
    }

    private val databaseModuleSource: String by lazy {
        ProjectFiles.sourceFile("di/DatabaseModule.kt").readText()
    }

    private fun schemaFile(version: Int): File =
        schemaFiles[version] ?: error("No exported schema for v$version")

    /** Version → its exported schema JSON, under the module's `schemas/` directory. */
    private val schemaFiles: Map<Int, File> by lazy { ProjectFiles.exportedSchemas() }
}
