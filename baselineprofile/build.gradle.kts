import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Drives the app to collect its baseline profile — see BaselineProfileGenerator.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.seblful.klas.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Baseline profiles are collected on API 28+; an unrooted phone needs 33+.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

baselineProfile {
    // The phone at hand, not a Gradle-managed emulator: none is configured for this project.
    useConnectedDevices = true
}

// The id of the app under test, which the generator launches: read from the APK, so the `.benchmark` suffix
// the app gives the profiled build reaches it without being written twice.
androidComponents {
    onVariants { variant ->
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { loader.load(it)?.applicationId ?: error("No application id in $it") },
        )
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
