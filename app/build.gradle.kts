import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

// Configure release signing only when keystore.properties exists.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.seblful.klas"
    // A published application ID cannot be changed.
    // Current AndroidX dependencies require API 37; the runtime target remains API 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.seblful.klas"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "0.4.6"

        testInstrumentationRunner = "com.seblful.klas.KlasTestRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Keep debug data and device integrations separate from release.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // The Baseline Profile plugin's builds of release — the one profiled, the one benchmarked — under an id
        // of their own: as `com.seblful.klas` they would install over the app a phone actually uses.
        create("nonMinifiedRelease") { applicationIdSuffix = ".benchmark" }
        create("benchmarkRelease") { applicationIdSuffix = ".benchmark" }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time is used throughout but is API 26+; desugar it down to minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // The app speaks only these (AppLanguage resolves every phone to one), so libraries' other locales
        // are dead weight in resources.arsc.
        localeFilters += listOf("en", "ru")
    }
    bundle {
        language {
            // Settings can switch languages independently of the phone locale.
            enableSplit = false
        }
    }
    lint {
        // AGP 9.2.1 release lint hits a Windows file-locking bug; lintDebug still runs.
        checkReleaseBuilds = false
    }
    // Keep unmocked Android calls failing in JVM tests; defaults hide missing test boundaries.
    // Robolectric gets merged resources. Device migration tests need Room’s exported schemas.
    sourceSets {
        getByName("androidTest").assets.directories.add("${project(":shared").projectDir}/schemas")
        getByName("androidTest").kotlin.srcDir(rootProject.file("shared/src/testFixtures/kotlin"))
        getByName("test").kotlin.srcDir(rootProject.file("shared/src/testFixtures/kotlin"))
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Match device rendering for screenshot tests.
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            if (project.hasProperty("storeScreenshots")) {
                it.systemProperty("klas.store.out", rootProject.file("store/build/raw").absolutePath)
                it.outputs.upToDateWhen { false }
            }
            // Robolectric needs these JDK 17+ reflective accesses.
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        // Compose Styles (`ui/theme/KlasStyles.kt`) are still experimental in foundation 1.12.
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

// Generate screenshot tests for every @Preview.
@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
    outputDir.set(file("src/test/screenshots"))
    compare {
        outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
    }
    generateComposePreviewRobolectricTests {
        enable = true
        packages = listOf("com.seblful.klas")
        includePrivatePreviews = true
        robolectricConfig = mapOf("qualifiers" to "\"xhdpi\"")
        testerQualifiedClassName = "com.seblful.klas.screenshot.KlasPreviewTester"
        useScanOptionParametersInTester = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigationevent.compose)

    // Applies the baseline profile to an APK installed from outside Play (the GitHub release, `adb install`).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.metrox.viewmodel.compose)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.preview)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.compose.preview.scanner.support)
    testImplementation(libs.composable.preview.scanner)
    testImplementation(libs.androidx.espresso.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.glance.appwidget.preview)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Use the bundled JVM SQLite driver and exclude Android native libraries from unit tests.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "androidx.sqlite", module = "sqlite-bundled-android")
}

composeCompiler {
    // Domain models and kotlinx-datetime values are immutable; see the file.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

// Share AGP's compiled tests and runtime, while leaving preview rendering to Roborazzi.
tasks.register<Test>("testDebugBehavior") {
    group = "verification"
    description = "Run Android JVM behavior tests without previews or screenshot capture."
    val fullSuite = tasks.named<Test>("testDebugUnitTest").get()
    dependsOn(fullSuite.taskDependencies)
    testClassesDirs = fullSuite.testClassesDirs
    classpath = fullSuite.classpath
    systemProperties(fullSuite.systemProperties)
    jvmArgs(fullSuite.jvmArgs.orEmpty())
    maxHeapSize = fullSuite.maxHeapSize
    filter {
        excludeTestsMatching("com.github.takahirom.roborazzi.*")
        excludeTestsMatching("com.seblful.klas.screenshot.*")
        excludeTestsMatching("com.seblful.klas.store.StoreScreenshots")
    }
}
