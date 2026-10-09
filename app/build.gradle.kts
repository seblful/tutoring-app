import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

// Release signing is configured only when keystore.properties exists (gitignored). This keeps debug
// builds, CI and contributors without the upload key working — the release build is simply unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.seblful.klas"
    // NOTE: the app was renamed from «Репетит» (com.seblful.repetit) before any Play upload, so the
    // application id could still change. It cannot again — Play keys a listing to it forever.
    // Latest androidx (core-ktx 1.19, lifecycle 2.11) require compiling against API 37; targetSdk
    // stays at 36 (a stable runtime behavior target) — compileSdk and targetSdk move independently.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.seblful.klas"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "0.4.3"

        testInstrumentationRunner = "com.seblful.klas.HiltTestRunner"
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
            // Its own application id, so a debug build installs *alongside* the signed release the
            // tutor actually uses instead of being refused for having a different signature — and,
            // more to the point, so testing never touches their real students, classes or payments.
            // The two apps keep separate databases, reminders and calendar events.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    bundle {
        language {
            // The language is chosen **inside the app** (Settings → Язык), not by the phone: an English
            // phone can be told to speak Russian, and a Russian one English. A per-language split would
            // install only the phone's own `values-*`, so switching in the app would find the other
            // language's strings missing. Every language ships in the base APK instead.
            enableSplit = false
        }
    }
    lint {
        // lintVitalAnalyzeRelease hits a file-locking bug in AGP 9.2.1's lint-jar migration on
        // Windows; lint still runs on debug builds (`lintDebug`), so skip it during release assembly.
        checkReleaseBuilds = false
    }
    // Deliberately no `unitTests.isReturnDefaultValues`. It makes an unmocked android.jar call return
    // null/0 instead of throwing, which turns "this test reached the Android framework" — something a
    // JVM test in this project should never do — into a silent pass on a zero value. The domain is
    // pure and every Android edge sits behind a port (CalendarSync, Notifier, DocumentStore,
    // ReminderScheduler, Transactor), so a test that needs the framework has found a missing port
    // rather than a reason to turn this back on.
    //
    // Robolectric tests are the exception that proves it: they get a real framework rather than the
    // stub android.jar, so they never reach a default value either. `isIncludeAndroidResources` is what
    // hands them the merged resources — the app's theme, strings and Golos Text font — without which a
    // screenshot would be of a theme-less, font-less component. It changes nothing for the plain JVM
    // tests, which never ask for a resource.
    //
    // Room's exported schema JSONs (see `ksp { room.schemaLocation }`) are shipped as androidTest assets so
    // `MigrationTestHelper` can stand a v5 database up on a device and validate the migration against the
    // bundled v6 schema — see `SettingsLanguageMigrationTest`. This wiring was deleted with the squashed
    // migration chain and is back because v6 owes a real migration from a released version; the JVM half
    // (`MigrationChainTest`) reads the same files off the tree and only proves the chain *exists*.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Roborazzi's advice for Robolectric native graphics: copy pixels through the hardware
            // renderer, which draws what a phone does (elevation shadows, rounded clips) rather than the
            // software path's approximation. Only a screenshot ever reads it.
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            // Robolectric reaches into JDK internals (a FileDescriptor's raw int, the font and text
            // classes) that JDK 17+ closes to reflection; without these every Robolectric test fails in
            // setup. The list is Robolectric's own documented one for JDK 17+ (robolectric.org/getting-started).
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

// Kotlin compiler options moved out of the deprecated android.kotlinOptions block (removed in AGP 9).
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

ksp {
    // Export each DB version's schema to /schemas so migrations can be tested (and reviewed in git).
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Screenshot tests: every @Preview in the app is drawn under Robolectric and compared with a committed
// golden — see docs/testing.md. Nothing here is a test to write: the plugin generates one test class
// that scans for previews, so a new @Preview is a new screenshot on the next record.
// Both the compare directory and the preview generation are still marked experimental by Roborazzi.
@OptIn(ExperimentalRoborazziApi::class)
roborazzi {
    // Goldens are source, reviewed in the diff like any other: not build output that `clean` deletes.
    outputDir.set(file("src/test/screenshots"))
    // Where a failed verify leaves `*_compare.png` (golden | diff | new): build output, uploaded by CI,
    // never committed.
    compare {
        outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
    }
    generateComposePreviewRobolectricTests {
        enable = true
        packages = listOf("com.seblful.klas")
        // The previews are private on purpose — they are not API — so the scan has to see private ones.
        includePrivatePreviews = true
        // xhdpi rather than Robolectric's mdpi default: an 11sp status pill drawn 11px high is not
        // something a reviewer can judge a diff of. The values are pasted into a @Config annotation as
        // source, hence the quotes inside the string.
        robolectricConfig = mapOf("qualifiers" to "\"xhdpi\"")
        // Ours, for the compare tolerance that lets a golden recorded on Windows pass on CI's Linux.
        testerQualifiedClassName = "com.seblful.klas.screenshot.KlasPreviewTester"
        // That tester keeps Roborazzi's own scan (it only changes the capture), and the default scan
        // already honours `includePrivatePreviews`; this tells the plugin so rather than refusing it.
        useScanOptionParametersInTester = true
    }
}

dependencies {
    // The pure-Kotlin rules. `implementation`, not `api`: nothing depends on an application module.
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines & serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Core library desugaring (java.time on API < 26)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Screenshot tests (Robolectric + Roborazzi), and the Robolectric runtime other framework-backed
    // JVM tests can share.
    testImplementation(libs.robolectric)
    // Repository, backup and view-model tests run on a real in-memory Room database (testutil/TestDatabase):
    // Robolectric supplies the Context Room asks for, the bundled driver the SQLite.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.compose.preview.scanner.support)
    testImplementation(libs.composable.preview.scanner)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
