import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Test doubles, fixtures and real in-memory databases for the test source sets of `:shared`, `:ui` and `:app`.
// Ships nothing: only test configurations depend on it.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.seblful.klas.testing"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    iosArm64()
    iosSimulatorArm64()

    // Fixtures set lessons up without an undo window; app code goes through LessonChanges.
    compilerOptions {
        optIn.add("com.seblful.klas.data.lessons.WritesWithoutUndo")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}
