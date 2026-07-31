package com.seblful.klas.data.local

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
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
 * "Bumped" means *past the baseline* — [AppDatabase.BASELINE_VERSION], the oldest version any database can
 * be at. Below it there is nothing to migrate from, which is why `Migrations.kt` does not exist; at or
 * above it, every single step owes one. The two numbers being equal is the state before a first release,
 * and the constant is read from source rather than assumed so that raising one without the other fails
 * here.
 *
 * The mistake this exists for is the quietest one in the project: bump `version`, forget the migration,
 * and Room finds no path from the version on the tutor's phone. A debug build then hands it to the
 * destructive fallback and a release build refuses to open at all (`di/DatabaseModule`) — neither of which
 * is an upgrade, and the first of which is indistinguishable from the app having lost every student,
 * lesson and payment.
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
    fun `the exported schemas are exactly the baseline and every version after it`() {
        assertEquals(
            "The exported schema JSONs disagree with the versions this app can meet: the baseline is " +
                "v$baselineVersion and AppDatabase declares v$declaredVersion. A migration is written " +
                "and validated against an exported schema, so a missing one cannot be tested — and a " +
                "schema below the baseline describes a version no database is at, which is a fixture " +
                "for a migration that should not exist (delete it, or lower the baseline and say why).",
            (baselineVersion..declaredVersion).toList(),
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
    fun `every step past the baseline ships a migration, and nothing below it does`() {
        assertEquals(
            "The baseline is v$baselineVersion and the schema is at v$declaredVersion, so Room needs a " +
                "migration for every single step between them — a gap is a released database with no " +
                "upgrade path, and a step below the baseline migrates a version nothing is at. Write " +
                "the missing migration in data/local/Migrations.kt (the file is absent entirely while " +
                "the two versions are equal, which is what having released nothing yet looks like).",
            (baselineVersion until declaredVersion).map { it to it + 1 },
            migrationSteps(),
        )
    }

    /**
     * The baseline is the oldest version a database can be at, so it cannot be *newer* than the schema.
     * Getting this backwards would silently excuse every migration the app owes.
     */
    @Test
    fun `the baseline is not above the version the app declares`() {
        assertEquals(
            "AppDatabase.BASELINE_VERSION (v$baselineVersion) is above the version it declares " +
                "(v$declaredVersion). The baseline is the oldest database that can exist, not the " +
                "newest — if the schema was just rolled back, lower the baseline to match and say why " +
                "no database is at the versions in between.",
            true,
            baselineVersion <= declaredVersion,
        )
    }

    @Test
    fun `the migrations are wired into the database that ships`() {
        assertEquals(
            "Migrations.kt is not what Room reads — DatabaseModule's builder is. A chain that exists " +
                "but is never passed to `addMigrations` leaves every upgrade with no path at all, " +
                "which is exactly the state having written the migration is supposed to rule out.",
            declaredVersion > baselineVersion,
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
            .findAll(appDatabaseSource)
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

    /**
     * The baseline the app claims, read from source beside [declaredVersion] and for the same reason: this
     * test's whole job is to catch the two of them being moved out of step.
     */
    private val baselineVersion: Int by lazy {
        val declarations = Regex("""BASELINE_VERSION\s*=\s*(\d+)""")
            .findAll(appDatabaseSource)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            "AppDatabase.kt should state its baseline version exactly once; found $declarations. See " +
                "BASELINE_VERSION for what the number claims — it is a statement about what has been " +
                "released, and this test reads it so that claim cannot drift from the schema.",
            1,
            declarations.size,
        )
        declarations.single().toInt()
    }

    /** Every version Room has exported a schema for, ascending. */
    private val exportedVersions: List<Int> by lazy { schemaFiles.keys.sorted() }

    /**
     * The `(from, to)` step of every migration the project declares, ascending — empty while
     * `Migrations.kt` does not exist, which is the state a schema sitting *at* its baseline is in.
     */
    private fun migrationSteps(): List<Pair<Int, Int>> {
        val source = ProjectFiles.sourceFileOrNull("data/local/Migrations.kt") ?: return emptyList()
        return Regex("""Migration\((\d+)\s*,\s*(\d+)\)""")
            .findAll(SourceText.code(source.readText()))
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .sortedBy { it.first }
            .toList()
    }

    /**
     * The two files this reads, as **code** — comments stripped, per [SourceText.code]. Both of them explain
     * in prose the very things scanned for here: `AppDatabase.BASELINE_VERSION` says what the squashed chain
     * used to be, and `DatabaseModule` says why it no longer calls `addMigrations`. Matching raw text made
     * writing the second of those explanations fail this test.
     */
    private val appDatabaseSource: String by lazy {
        SourceText.code(ProjectFiles.sourceFile("data/local/AppDatabase.kt").readText())
    }

    private val databaseModuleSource: String by lazy {
        SourceText.code(ProjectFiles.sourceFile("di/DatabaseModule.kt").readText())
    }

    private fun schemaFile(version: Int): File =
        schemaFiles[version] ?: error("No exported schema for v$version")

    /** Version → its exported schema JSON, under the module's `schemas/` directory. */
    private val schemaFiles: Map<Int, File> by lazy { ProjectFiles.exportedSchemas() }
}
