package com.seblful.klas.testutil

import java.io.File

/**
 * Resolves project files and source roots for source-based tests. Paths work from both Gradle's module
 * directory and IDE configurations that start at the repository root.
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

    /** An Android, shared or UI source file, relative to its package root. */
    fun sourceFileOrNull(relative: String): File? = moduleFileOrNull("$SOURCE_ROOT/$relative")
        ?: siblingModuleFileOrNull("shared", "src/commonMain/kotlin/com/seblful/klas/$relative")
        ?: siblingModuleFileOrNull("ui", "src/commonMain/kotlin/com/seblful/klas/$relative")
        ?: siblingModuleFileOrNull("ui", "src/androidMain/kotlin/com/seblful/klas/$relative")

    /**
     * An Android or shared common source file, e.g. `data/lessons/LessonsDaos.kt`. A test asserts
     * something about the source text, so the failure names the file: if it moved, the test has to be
     * pointed at wherever it now lives rather than quietly finding nothing.
     */
    fun sourceFile(relative: String): File = sourceFileOrNull(relative)
        ?: error("Could not find `$SOURCE_ROOT/$relative` from ${System.getProperty("user.dir")}")

    /**
     * Every `.kt` file under [SOURCE_ROOT] — for a test asserting something about the app's own shape.
     *
     * Includes shared common and platform sources and the domain, so moving code between modules
     * cannot bypass a repository-wide check.
     */
    fun sources(): List<File> {
        val roots = listOf(
            moduleFileOrNull(SOURCE_ROOT),
            siblingModuleFileOrNull(DOMAIN_MODULE, DOMAIN_SOURCE_ROOT),
            siblingModuleFileOrNull("shared", DOMAIN_SOURCE_ROOT),
            siblingModuleFileOrNull("shared", "src/androidMain/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("shared", "src/iosMain/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("shared", "src/jvmMain/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("ui", DOMAIN_SOURCE_ROOT),
            siblingModuleFileOrNull("ui", "src/androidMain/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("ui", "src/iosMain/kotlin/com/seblful/klas"),
        )
        check(roots.all { it != null }) {
            "Could not find `$SOURCE_ROOT` in `app` and `$DOMAIN_SOURCE_ROOT` in `$DOMAIN_MODULE` from " +
                System.getProperty("user.dir")
        }
        return roots.filterNotNull().flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }
        }
    }

    /**
     * The text catalog's sources: `values…/strings.xml` in the Android format, one folder per language,
     * which `:ui` compiles into `Strings`, `Plurals` and `StringArrays`.
     */
    fun strings(): File = siblingModuleFileOrNull("ui", "src/commonMain/strings")
        ?: error("Could not find `ui/src/commonMain/strings` from ${System.getProperty("user.dir")}")

    /** A Compose resource of the shared UI, e.g. `font/golos_text_bold.ttf`. */
    fun composeResource(relative: String): File =
        siblingModuleFileOrNull("ui", "src/commonMain/composeResources/$relative")
            ?: error("Could not find compose resource `$relative` from ${System.getProperty("user.dir")}")

    /** Feature DAOs, excluding SnapshotDao's intentional whole-table operations. */
    fun daoSources(): List<File> = sources().filter {
        it.name != "SnapshotDao.kt" && (it.name.endsWith("Dao.kt") || it.name.endsWith("Daos.kt"))
    }

    private const val DOMAIN_MODULE = "domain"

    private const val DOMAIN_SOURCE_ROOT = "src/commonMain/kotlin/com/seblful/klas"

    private fun siblingModuleFileOrNull(module: String, relative: String): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, "$module/$relative").takeIf { it.exists() }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }

    /** Where the package's unit-test sources live, relative to the module. */
    const val TEST_SOURCE_ROOT = "src/test/java/com/seblful/klas"

    /**
     * A unit-test source file under [TEST_SOURCE_ROOT], e.g. `testutil/Fixtures.kt`.
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
        val roots = listOf(
            moduleFileOrNull(TEST_SOURCE_ROOT),
            siblingModuleFileOrNull("domain", "src/commonTest/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("shared", "src/commonTest/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("shared", "src/jvmTest/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("shared", "src/iosTest/kotlin/com/seblful/klas"),
            siblingModuleFileOrNull("shared", "src/testFixtures/kotlin/com/seblful/klas"),
        )
        check(roots.all { it != null }) { "Could not find every module's test sources" }
        return roots.filterNotNull().flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }
        }
    }

    /**
     * Every schema version Room has exported, mapped to its JSON. Keyed by the file name, which is
     * the version — that is the whole naming convention Room uses under `schemas/`.
     */
    fun exportedSchemas(): Map<Int, File> {
        val dir = siblingModuleFileOrNull("shared", "schemas")
            ?: error("Could not find the shared exported schemas directory")
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
