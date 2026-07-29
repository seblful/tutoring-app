package com.seblful.klas.testutil

import java.io.File

/**
 * Finds the project's own files from inside a unit test.
 *
 * The tripwires derive their expectations from a real build artifact rather than restating them, and so
 * have to read one off disk. They come in two kinds. Some read a **named** artifact:
 * `StoredEnumNamesTest` the DAO source (`@Query` is not retained at runtime), `MigrationChainTest`
 * `AppDatabase.kt` (`@Database` is `BINARY`-retained), both it and `BackupSchemaCoverageTest` the
 * exported schema JSON, and `WholeTableWritesTest` the two DAO files a wipe may and may not appear in.
 * The rest **scan the tree** for something that must not reappear anywhere: `ReportsSurfaceTest` for the
 * app reaching past `Reports.of`, `ScreenStateSurfaceTest` for a hand-written sharing tail,
 * `CardPressFeedbackTest` for a card taking its tap from a `clickable`, `DirectSeedingRatchetTest` for
 * tests seeding through a DAO.
 *
 * The first three had each grown their own copy of the walk-up-from-the-working-directory search — the
 * same twenty lines three times, with three different failure messages and three places to fix when a
 * path moves.
 *
 * The walk itself is the part worth having once: the working directory is the `app` module under
 * Gradle but the repo root under some IDE run configurations, so a fixed relative path works in one
 * and not the other.
 */
object ProjectFiles {

    /** Where the package's sources live, relative to the module. */
    const val SOURCE_ROOT = "src/main/java/com/seblful/klas"

    /** A path relative to the `app` module, or null when it does not exist. */
    fun moduleFileOrNull(relative: String): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            listOf(File(dir, relative), File(dir, "app/$relative"))
                .firstOrNull { it.exists() }
                ?.let { return it }
            dir = dir.parentFile
        }
        return null
    }

    /** A path relative to the `app` module. */
    fun moduleFile(relative: String): File = moduleFileOrNull(relative)
        ?: error("Could not find `$relative` from ${System.getProperty("user.dir")}")

    /** A source file under [SOURCE_ROOT], e.g. `data/local/Daos.kt`, or null when absent. */
    fun sourceFileOrNull(relative: String): File? = moduleFileOrNull("$SOURCE_ROOT/$relative")

    /**
     * A source file under [SOURCE_ROOT], e.g. `data/local/Daos.kt`. A test reading one is asserting
     * something about the source text, so the failure names the file: if it moved, the test has to be
     * pointed at wherever it now lives rather than quietly finding nothing.
     */
    fun sourceFile(relative: String): File = sourceFileOrNull(relative)
        ?: error("Could not find `$SOURCE_ROOT/$relative` from ${System.getProperty("user.dir")}")

    /** Every `.kt` file under [SOURCE_ROOT] — for a test asserting something about the app's own shape. */
    fun sources(): List<File> {
        val root = moduleFileOrNull(SOURCE_ROOT)
            ?: error("Could not find `$SOURCE_ROOT` from ${System.getProperty("user.dir")}")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Where the package's unit-test sources live, relative to the module. */
    const val TEST_SOURCE_ROOT = "src/test/java/com/seblful/klas"

    /**
     * A unit-test source file under [TEST_SOURCE_ROOT], e.g. `testutil/FakeDaos.kt`.
     *
     * The doubles are worth scanning for the same reason the DAOs are: a fake that restates a stored
     * enum name or a piece of production arithmetic can be left behind by a rename that the compiler
     * follows everywhere else, and the result is a suite that passes about behaviour the app no longer
     * has.
     */
    fun testSourceFile(relative: String): File = moduleFileOrNull("$TEST_SOURCE_ROOT/$relative")
        ?: error("Could not find `$TEST_SOURCE_ROOT/$relative` from ${System.getProperty("user.dir")}")

    /** Every `.kt` file under [TEST_SOURCE_ROOT] — for the one test that scans the suite itself. */
    fun testSources(): List<File> {
        val root = moduleFileOrNull(TEST_SOURCE_ROOT)
            ?: error("Could not find `$TEST_SOURCE_ROOT` from ${System.getProperty("user.dir")}")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Every schema version Room has exported, mapped to its JSON. Keyed by the file name, which is
     * the version — that is the whole naming convention Room uses under `schemas/`.
     */
    fun exportedSchemas(): Map<Int, File> {
        val dir = moduleFileOrNull("schemas") ?: error("Could not find the exported schemas directory")
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file -> file.nameWithoutExtension.toIntOrNull()?.let { it to file } }
            .toMap()
    }

    /** The highest-versioned exported schema — the one describing the database as it stands. */
    fun newestExportedSchema(): File {
        val schemas = exportedSchemas()
        val newest = schemas.keys.maxOrNull() ?: error("No exported schema JSON under `schemas/`")
        return schemas.getValue(newest)
    }
}
