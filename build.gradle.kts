// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the kotlin.android plugin is gone. AGP ships
// with an older default KGP; pin it to our catalog Kotlin so AGP's Kotlin compiler, the
// Compose/serialization compiler plugins, and KSP (which embeds the same version) all line up.
//
// The literal below has to be repeated rather than read from the catalog: `libs` accessors do not
// exist yet while `buildscript {}` is being evaluated. The check after the `plugins` block is what
// keeps the repetition honest — see there.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// ktlint (formatting) and detekt (static smells) on every module, from here rather than from each
// module's script: a module added later is linted without anyone remembering to opt it in. Every
// module in this build is Kotlin, so there is no "is it Kotlin?" test to get wrong.
//
// Both are enforced from the day they were added without reformatting the code they found: each
// module carries a baseline of the findings that existed then (config/ktlint/baseline.xml,
// config/detekt/baseline.xml under the module). New code is held to the rules. Regenerating the
// baselines is in the README (Build & Test).
val detektVersion = libs.versions.detekt.get()
val ktlintVersion = libs.versions.ktlint.get()
val detektConfig = rootProject.file("config/detekt/detekt.yml")
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version = ktlintVersion
        // Rules come from the root .editorconfig, the same file the IDE formatter reads, so
        // "Reformat Code" and ktlintFormat agree.
        baseline = file("config/ktlint/baseline.xml")
    }

    apply(plugin = "dev.detekt")
    configure<dev.detekt.gradle.extensions.DetektExtension> {
        toolVersion = detektVersion
        buildUponDefaultConfig = true
        config.setFrom(detektConfig)
        baseline = file("config/detekt/baseline.xml")
        // Every source set, not detekt's default of main + test: the device tests and the debug-only
        // previews are this project's code too. detekt reads only .kt/.kts out of the tree.
        source.setFrom("src")
    }
}

// The pin above and the catalog's `kotlin` must be the same version: they are AGP's Kotlin compiler
// and the compiler *plugins* it has to load, and a mismatch is the sort of thing that surfaces as an
// unrelated-looking codegen or Compose error rather than as a version complaint. Bumping one and
// forgetting the other is the whole risk of stating it twice, so it fails here instead — now, at
// configuration time, naming both numbers.
run {
    val pinned = buildscript.configurations.getByName("classpath")
        .allDependencies
        .firstOrNull { it.group == "org.jetbrains.kotlin" && it.name == "kotlin-gradle-plugin" }
        ?.version
    val catalog = libs.versions.kotlin.get()
    check(pinned == catalog) {
        "The Kotlin Gradle plugin pinned in build.gradle.kts ($pinned) and the version catalog's " +
            "`kotlin` ($catalog) disagree. Set both to the same version — the catalog is the one to " +
            "follow, and it documents why Kotlin cannot move past KSP."
    }
}
