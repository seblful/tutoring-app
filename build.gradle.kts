// AGP's Kotlin compiler and its plugins must match the catalog version.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}


plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    jacoco
}

// Lint every module; detekt baselines record existing findings.
val detektVersion = libs.versions.detekt.get()
val ktlintVersion = libs.versions.ktlint.get()
val detektConfig = rootProject.file("config/detekt/detekt.yml")
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version = ktlintVersion
        filter {
            exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
        }
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

tasks.register("verify") {
    group = "verification"
    description = "Run JVM tests, screenshots, Android lint, formatting, static analysis and debug builds."
    dependsOn(
        "testStore",
        "testDeviceTools",
        gradle.includedBuild("build-logic").task(":test"),
        ":domain:jvmTest", ":shared:jvmTest", ":ui:testAndroidHostTest", ":app:verifyRoborazziDebug", ":app:lintDebug",
        ":app:assembleDebug", ":app:assembleDebugAndroidTest",
    )
    listOf(":app", ":domain", ":shared", ":ui", ":iosHost", ":baselineprofile").forEach { module ->
        dependsOn("$module:ktlintCheck", "$module:detekt")
    }
}

// Coverage of the JVM suites. :app is measured by its behavior tests, not the preview screenshots, which
// render code without asserting on it; they also cover the :ui screens they drive. :shared is measured by
// its own tests only: :app runs its Android build, whose classes do not match the JVM ones the report reads.
val jacocoVersion = libs.versions.jacoco.get()
jacoco { toolVersion = jacocoVersion }
val coverageTests = mapOf(":app" to "testDebugBehavior", ":domain" to "jvmTest", ":shared" to "jvmTest")
coverageTests.forEach { (path, coverageTest) ->
    project(path) {
        apply(plugin = "jacoco")
        configure<JacocoPluginExtension> { toolVersion = jacocoVersion }
        tasks.withType<Test>().configureEach {
            configure<JacocoTaskExtension> {
                // The agent slows tests down; screenshot timing in the full :app suite is sensitive to that.
                isEnabled = name == coverageTest
                // Robolectric defines app classes in its own loader, which Jacoco only sees with this.
                isIncludeNoLocationClasses = true
                includes = listOf("com.seblful.klas.*")
            }
        }
    }
}

tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "Run the JVM test suites and report their line and branch coverage."
    dependsOn(coverageTests.map { (path, test) -> "$path:$test" })
    executionData.from(coverageTests.map { (path, test) -> file("${path.drop(1)}/build/jacoco/$test.exec") })
    val generated = listOf(
        "**/*\$Metro*", "metro/**", // Metro factories, graphs and hints
        "**/*_Impl*", // Room DAOs and databases
        "**/ComposableSingletons*",
        "**/preview/**", // debug-only @Preview functions
        "**/BuildConfig.class",
    )
    classDirectories.from(
        listOf(
            "app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
            "ui/build/classes/kotlin/android/main",
            "domain/build/classes/kotlin/jvm/main",
            "shared/build/classes/kotlin/jvm/main",
        ).map { fileTree(it) { exclude(generated) } },
    )
    sourceDirectories.from(
        "app/src/main/java",
        "app/src/debug/java",
        "ui/src/commonMain/kotlin",
        "ui/src/androidMain/kotlin",
        "domain/src/commonMain/kotlin",
        "shared/src/commonMain/kotlin",
        "shared/src/jvmMain/kotlin",
    )
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.register<Exec>("testStore") {
    group = "verification"
    description = "Run the store image and video tooling tests."
    commandLine("node", "--test", "store/**/*.test.mjs")
}

tasks.register("testQuick") {
    group = "verification"
    description = "Run domain, shared, UI, Android behavior and store tooling tests without screenshots."
    dependsOn(
        ":domain:jvmTest", ":shared:jvmTest", ":ui:testAndroidHostTest", ":app:testDebugBehavior", "testStore",
        "testDeviceTools",
    )
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}

tasks.register<Exec>("testDeviceTools") {
    group = "verification"
    description = "Check Android relaunch orchestration and failure reporting without a device."
    commandLine("node", "--test", "scripts/**/*.test.mjs")
}
