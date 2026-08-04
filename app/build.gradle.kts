import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
        versionCode = 6
        versionName = "0.3.2"

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
            // The app is Russian-only: every string lives in the unqualified `values/`, and the
            // resource locale is pinned to Russian so plurals decline by Russian rules (see
            // `RussianLocale`). There is therefore no per-language split for a device to be missing
            // — splitting by language could only ever drop the one resource set there is.
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
    // Room's exported schema JSONs (see `ksp { room.schemaLocation }`) are shipped as androidTest assets so
    // `MigrationTestHelper` can stand a v5 database up on a device and validate the migration against the
    // bundled v6 schema — see `SettingsLanguageMigrationTest`. This wiring was deleted with the squashed
    // migration chain and is back because v6 owes a real migration from a released version; the JVM half
    // (`MigrationChainTest`) reads the same files off the tree and only proves the chain *exists*.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
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

dependencies {
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
