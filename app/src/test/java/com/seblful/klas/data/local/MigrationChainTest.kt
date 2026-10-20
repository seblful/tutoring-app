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
            "Android must use the shared database configuration",
            true,
            databaseModuleSource.contains("openDatabase("),
        )
        assertEquals(
            "Migrations.kt is not what Room reads — the shared database builder is. A chain that exists " +
                "but is never passed to `addMigrations` leaves every upgrade with no path at all, " +
                "which is exactly the state having written the migration is supposed to rule out.",
            declaredVersion > baselineVersion,
            databaseFactorySource.contains("addMigrations(*Migrations.ALL)"),
        )
    }

    @Test
    fun `a release build has no destructive path`() {
        assertEquals(
            "Destructive fallback belongs to the shared factory's guarded option",
            false,
            databaseModuleSource.contains("fallbackToDestructiveMigration"),
        )
        if (!databaseFactorySource.contains("fallbackToDestructiveMigration")) return
        assertEquals(
            "The shared factory enables destructive migration without a default-off host option, " +
                "so a release build would answer a missing migration by dropping every table. Keep " +
                "the guard (rename it here if the flag is now called something else) — a release " +
                "build must fail to open a database it cannot migrate, leaving the rows on disk for " +
                "the next build to migrate properly.",
            true,
            databaseFactorySource.contains("if (allowDestructiveMigration)") &&
                databaseFactorySource.contains("allowDestructiveMigration: Boolean = false") &&
                databaseModuleSource.contains("allowDestructiveMigration = debugBuild"),
        )
    }

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

    private val exportedVersions: List<Int> by lazy { schemaFiles.keys.sorted() }

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

    @Test
    fun `every table and index a migration creates is the one the version it ends at declares`() {
        for (migration in Migrations.ALL) {
            val to = migration.endVersion
            val schema = tables(to)
            val declared = schema.values.flatMap { listOf(it.createSql) + it.indices }.toSet()
            val created = executedSql(migration).filter {
                it.startsWith("CREATE TABLE") || it.startsWith("CREATE INDEX")
            }
            assertEquals(
                "MIGRATION_${migration.startVersion}_$to creates a table or index that is not v$to's exported one. " +
                    "Copy the statement from shared/schemas/…/$to.json (with the table's name for \${TABLE_NAME}).",
                emptyList<String>(),
                created - declared,
            )
        }
    }

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

                val copied = sql.firstNotNullOfOrNull {
                    COPY.matchEntire(it)?.takeIf { m -> m.groupValues[1] == table }
                }
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

    private fun classesViewSql(version: Int): String? =
        database(version)["views"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .singleOrNull { it.getValue("viewName").jsonPrimitive.content == "classes" }
            ?.getValue("createSql")?.jsonPrimitive?.content
            ?.removePrefix("CREATE VIEW `\${VIEW_NAME}` AS ")

    private data class TableSchema(
        val createSql: String,
        val indices: List<String>,
        val columns: Set<String>,
        val references: Set<String>,
    )

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

    private fun migrationSteps(): List<Pair<Int, Int>> {
        val source = ProjectFiles.sourceFileOrNull("data/local/Migrations.kt") ?: return emptyList()
        return Regex("""Migration\((\d+)\s*,\s*(\d+)\)""")
            .findAll(SourceText.code(source.readText()))
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .sortedBy { it.first }
            .toList()
    }

    private val appDatabaseSource: String by lazy {
        SourceText.code(ProjectFiles.sourceFile("data/local/AppDatabase.kt").readText())
    }

    private val databaseModuleSource: String by lazy {
        SourceText.code(ProjectFiles.sourceFile("di/DatabaseModule.kt").readText())
    }

    private val databaseFactorySource: String by lazy {
        SourceText.code(ProjectFiles.sourceFile("data/local/DatabaseFactory.kt").readText())
    }

    private fun schemaFile(version: Int): File =
        schemaFiles[version] ?: error("No exported schema for v$version")

    private val schemaFiles: Map<Int, File> by lazy { ProjectFiles.exportedSchemas() }

    private companion object {
        const val CREATE_VIEW = "CREATE VIEW `classes` AS "

        /** `ALTER TABLE `t` RENAME TO `aside`` — a table set aside to be rebuilt. */
        val RENAME_ASIDE = Regex("""ALTER TABLE `(\w+)` RENAME TO `(\w+)`""")

        /** `INSERT INTO `t` (columns) SELECT columns FROM `aside`` — a rebuilt table refilled. */
        val COPY = Regex("""INSERT INTO `(\w+)` \(([^)]*)\) SELECT (.*) FROM `(\w+)`""")
    }
}
