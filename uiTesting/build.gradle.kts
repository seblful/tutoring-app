import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Fixtures and port doubles for `:ui`'s screens and view models, shared by `:ui`'s common tests and `:app`'s
// Compose tests. Ships nothing: only test configurations depend on it.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.seblful.klas.uitesting"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":ui"))
            api(project(":testing"))
        }
        androidMain.dependencies {
            // Maps kotlin.test's @BeforeTest/@AfterTest to JUnit 4, which Robolectric runs too.
            api(kotlin("test-junit"))
            api(libs.robolectric)
        }
    }
}
