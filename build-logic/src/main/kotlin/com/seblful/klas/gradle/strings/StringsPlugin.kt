package com.seblful.klas.gradle.strings

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Registers `generateTextCatalog`: each `src/commonMain/strings/values…/strings.xml` → Kotlin. The module adds
 * the task to a source set (`kotlin.srcDir(task)`), which also orders compilation after it.
 */
class StringsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("generateTextCatalog", GenerateTextCatalog::class.java) {
            group = "build"
            description = "Generate the typed text catalog from the Android-format string XML."
            sources.convention(project.layout.projectDirectory.dir("src/commonMain/strings"))
            outputDirectory.convention(project.layout.buildDirectory.dir("generated/textCatalog"))
        }
    }
}

@CacheableTask
abstract class GenerateTextCatalog : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    /** The language of the unqualified `values/` folder. */
    @get:Input
    abstract val defaultTag: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val languages = sources.get().asFile.listFiles().orEmpty()
            .filter { it.isDirectory && it.resolve("strings.xml").isFile }
            .associate { folder ->
                val tag = tagOf(folder.name) ?: defaultTag.get()
                val file = folder.resolve("strings.xml")
                tag to AndroidStrings.parse(file.readText(), "${folder.name}/strings.xml")
            }
        val target = outputDirectory.get().asFile.resolve(packageName.get().replace('.', '/'))
        outputDirectory.get().asFile.deleteRecursively()
        target.mkdirs()
        CatalogWriter(packageName.get(), defaultTag.get()).write(languages).forEach { (name, text) ->
            target.resolve(name).writeText(text)
        }
    }

    companion object {
        private val FOLDER = Regex("""values(?:-([a-z]{2,3})(?:-r([A-Z]{2}))?)?""")

        /** `values-pt-rBR` → `pt-BR`; null for the default `values`. */
        fun tagOf(folder: String): String? {
            val match = requireNotNull(FOLDER.matchEntire(folder)) { "Unsupported resource folder '$folder'" }
            val (language, region) = match.destructured
            return when {
                language.isEmpty() -> null
                region.isEmpty() -> language
                else -> "$language-$region"
            }
        }
    }
}
