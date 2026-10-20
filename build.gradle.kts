// AGP's Kotlin compiler and its plugins must match the catalog version.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Lint every module; detekt baselines record existing findings.
val detektVersion = libs.versions.detekt.get()
val ktlintVersion = libs.versions.ktlint.get()
val detektConfig = rootProject.file("config/detekt/detekt.yml")
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version = ktlintVersion
    }

    apply(plugin = "dev.detekt")
    configure<dev.detekt.gradle.extensions.DetektExtension> {
        toolVersion = detektVersion
        buildUponDefaultConfig = true
        config.setFrom(detektConfig)
        baseline = file("config/detekt/baseline.xml")
        // Include device tests and debug previews as well as main and test.
        source.setFrom("src")
    }
}

// Fail early if the Gradle plugin and Kotlin compiler versions diverge.
run {
    val pinned = buildscript.configurations.getByName("classpath")
        .allDependencies
        .firstOrNull { it.group == "org.jetbrains.kotlin" && it.name == "kotlin-gradle-plugin" }
        ?.version
    val catalog = libs.versions.kotlin.get()
    check(pinned == catalog) {
        "The Kotlin Gradle plugin pinned in build.gradle.kts ($pinned) and the version catalog's " +
            "`kotlin` ($catalog) disagree. Set both to the same version — the catalog is the one to " +
            "follow."
    }
}
