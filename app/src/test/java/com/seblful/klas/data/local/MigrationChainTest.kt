package com.seblful.klas.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

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
     * **Every step leaves the `classes` view exactly as the version it ends at declares it** — and since
     * v10, which has none, that means dropped.
     *
     * Room validates views after a migration and recreates none of them, so a step that changed a column
     * the view projected had to drop the view and rebuild it with *that version's* `SELECT`, character for
     * character, or the upgrade died with `Migration didn't properly handle: classes` - on first launch
     * after an update, with the whole step rolled back. `MIGRATION_6_7` got this wrong twice: it shipped
     * onto one phone without rebuilding the view, and later rebuilt it from the shared `SELECT` after v8 had
     * moved that on, which put the v8 view onto a v7 schema. That is why v10 has no view; the steps up to
     * it still build theirs, and still have to build the right one.
     *
     * Neither needs a device to catch. Each step runs against a database that only records its SQL, and
     * the view it leaves - the one it built, the one it started with, or none - is compared with the
     * exported schema of the version it ends at. Whether that SQL *runs* is still the instrumented tests'
     * question.
     */
    @Test
    fun `every migration leaves the classes view as the version it ends at declares it`() {
        for (migration in Migrations.ALL) {
            val from = migration.startVersion
            val to = migration.endVersion
            val sql = executedSql(migration)
            val built = sql.lastOrNull { it.startsWith(CREATE_VIEW) }?.removePrefix(CREATE_VIEW)
            val dropped = sql.any { it.startsWith("DROP VIEW") && "`classes`" in it }
            assertEquals(
                "MIGRATION_${from}_$to leaves a `classes` view that is not v$to's. A step that changes a " +
                    "column the view projects must DROP it and CREATE it with v$to's SELECT, kept frozen " +
                    "in Migrations.kt; a version with no view must find it dropped (null means none).",
                classesViewSql(to),
                built ?: if (dropped) null else classesViewSql(from),
            )
        }
    }

    /**
     * **Every table and index a step creates is the one the version it ends at declares**, statement for
     * statement — Room's own DDL from the exported schema.
     *
     * Room validates the tables after the migrations, so a wrong `CREATE` does not lose data; it fails the
     * upgrade on first launch, rolled back, which only a device test would otherwise show. A table that is
     * *rebuilt* is where this matters most: the step writes its whole definition out by hand, defaults and
     * foreign keys included.
     */
    @Test
    fun `every table and index a migration creates is the one the version it ends at declares`() {
        for (migration in Migrations.ALL) {
            val to = migration.endVersion
            val schema = tables(to)
            val declared = schema.values.flatMap { listOf(it.createSql) + it.indices }.toSet()
            val created = executedSql(migration).filter { it.startsWith("CREATE TABLE") || it.startsWith("CREATE INDEX") }
            assertEquals(
                "MIGRATION_${migration.startVersion}_$to creates a table or index that is not v$to's exported one. " +
                    "Copy the statement from app/schemas/…/$to.json (with the table's name for \${TABLE_NAME}).",
                emptyList<String>(),
                created - declared,
            )
        }
    }

    /**
     * **A table rebuilt to drop a column comes back whole**: created as the version declares it, refilled
     * with every one of its columns, its indices recreated once the old copy is gone — and the old copy
     * dropped only after every table that refers to it.
     *
     * The last part is the one that loses data. `DROP COLUMN` refuses an indexed or foreign-key column (and
     * Android's own SQLite had none below Android 12), so a column goes by renaming the table aside, creating it again and copying the rows across ([Migrations.MIGRATION_9_10]).
     * With foreign keys on, `DROP TABLE` deletes every row first and fires `ON DELETE` actions: drop
     * the old `lessons` while a table still points at it and `attendance`'s `CASCADE` empties it. From
     * SQLite 3.26 a rename also re-points every foreign key that named the table at its new name — so a
     * table referring to a rebuilt one has to be rebuilt too, and its old copy dropped first; left alone,
     * it would end up pointing at the copy being dropped.
     */
    @Test
    fun `every table a migration rebuilds comes back whole, and its old copy goes after its children`() {
        for (migration in Migrations.ALL) {
            val step = "MIGRATION_${migration.startVersion}_${migration.endVersion}"
            val sql = executedSql(migration)
            val before = tables(migration.startVersion)
            val after = tables(migration.endVersion)
            val asides = sql.mapNotNull { RENAME_ASIDE.matchEntire(it)?.destructured }
                .associate { (table, aside) -> table to aside }

            for ((table, aside) in asides) {
                val schema = after.getValue(table)
                val dropAside = sql.indexOf("DROP TABLE `$aside`")
                assertTrue("$step sets `$table` aside as `$aside` and never drops it", dropAside >= 0)
                assertTrue("$step never creates `$table` again", schema.createSql in sql)

                val copied = sql.firstNotNullOfOrNull { COPY.matchEntire(it)?.takeIf { m -> m.groupValues[1] == table } }
                assertTrue("$step never copies `$aside` back into `$table`", copied != null)
                val (_, _, into, selected, source) = copied!!.groupValues
                assertEquals("$step copies `$table` from somewhere else", aside, source)
                assertEquals("$step's copy into `$table` names other columns than it reads", into, selected)
                assertEquals(
                    "$step leaves columns of `$table` behind (or copies some v${migration.endVersion} lacks)",
                    schema.columns,
                    into.split(",").map { it.trim().removeSurrounding("`") }.toSet(),
                )

                for (index in schema.indices) {
                    assertTrue(
                        "$step creates $index before dropping `$aside`, while the old table still holds the " +
                            "name — so it creates nothing",
                        sql.indexOf(index) > dropAside,
                    )
                }

                val children = before.filterValues { table in it.references }.keys
                for (child in children) {
                    val childAside = asides[child]
                    assertTrue(
                        "$step rebuilds `$table`, which `$child` refers to, without rebuilding `$child`. On " +
                            "SQLite 3.26+ the rename re-points `$child` at `$aside`, and dropping that " +
                            "cascades `$child` away.",
                        childAside != null,
                    )
                    assertTrue(
                        "$step drops `$aside` before `$childAside`, which still refers to it — with foreign " +
                            "keys on, that cascades `$child`'s rows away.",
                        sql.indexOf("DROP TABLE `$childAside`") < dropAside,
                    )
                }
            }
        }
    }

    /**
     * The SQL [migration] runs, recorded by a connection that runs none of it. Every statement it prepares
     * steps over no rows — the answer a check finding nothing gives — and may be closed, and nothing else,
     * so a step that starts reading something new fails here rather than being half-recorded.
     */
    private fun executedSql(migration: Migration): List<String> {
        val statements = mutableListOf<String>()
        val recorder = object : SQLiteConnection {
            override fun prepare(sql: String): SQLiteStatement {
                statements += sql
                return noRows(sql)
            }

            override fun close() = Unit
        }
        migration.migrate(recorder)
        return statements
    }

    /** A statement over no rows: every step finds none, and closing it is fine. */
    private fun noRows(sql: String): SQLiteStatement = Proxy.newProxyInstance(
        SQLiteStatement::class.java.classLoader,
        arrayOf(SQLiteStatement::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "step" -> false
            "close" -> null
            else -> error("`$sql` was asked SQLiteStatement.${method.name}; teach noRows() to answer it.")
        }
    } as SQLiteStatement

    /** The `classes` view's `SELECT` as v[version]'s exported schema declares it, or null if it has none. */
    private fun classesViewSql(version: Int): String? =
        database(version)["views"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .singleOrNull { it.getValue("viewName").jsonPrimitive.content == "classes" }
            ?.getValue("createSql")?.jsonPrimitive?.content
            ?.removePrefix("CREATE VIEW `\${VIEW_NAME}` AS ")

    /** One table of an exported schema: Room's DDL for it and its indices, its columns, and its parents. */
    private data class TableSchema(
        val createSql: String,
        val indices: List<String>,
        val columns: Set<String>,
        val references: Set<String>,
    )

    /** Every table of v[version]'s exported schema, by name, with `${TABLE_NAME}` filled in. */
    private fun tables(version: Int): Map<String, TableSchema> =
        database(version).getValue("entities").jsonArray.map { it.jsonObject }.associate { entity ->
            val name = entity.getValue("tableName").jsonPrimitive.content
            fun ddl(sql: String) = sql.replace("\${TABLE_NAME}", name)
            name to TableSchema(
                createSql = ddl(entity.getValue("createSql").jsonPrimitive.content),
                indices = entity["indices"]?.jsonArray.orEmpty()
                    .map { ddl(it.jsonObject.getValue("createSql").jsonPrimitive.content) },
                columns = entity.getValue("fields").jsonArray
                    .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }.toSet(),
                references = entity["foreignKeys"]?.jsonArray.orEmpty()
                    .map { it.jsonObject.getValue("table").jsonPrimitive.content }.toSet(),
            )
        }

    private fun database(version: Int): JsonObject =
        Json.parseToJsonElement(schemaFile(version).readText()).jsonObject.getValue("database").jsonObject

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

    private companion object {
        const val CREATE_VIEW = "CREATE VIEW `classes` AS "

        /** `ALTER TABLE `t` RENAME TO `aside`` — a table set aside to be rebuilt. */
        val RENAME_ASIDE = Regex("""ALTER TABLE `(\w+)` RENAME TO `(\w+)`""")

        /** `INSERT INTO `t` (columns) SELECT columns FROM `aside`` — a rebuilt table refilled. */
        val COPY = Regex("""INSERT INTO `(\w+)` \(([^)]*)\) SELECT (.*) FROM `(\w+)`""")
    }
}
