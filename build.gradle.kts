// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the kotlin.android plugin is gone. AGP ships
// with an older default KGP; pin it to our catalog Kotlin (2.3.20) so AGP's Kotlin compiler, the
// Compose/serialization compiler plugins, and KSP (which embeds 2.3.20) all line up.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
