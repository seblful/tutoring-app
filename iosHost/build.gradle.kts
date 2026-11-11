// The iOS host, as `:app` is Android's: the app's graph, its iOS adapters and its entry point, linked by
// `iosApp/` as the static `KlasApp` framework. The screens and everything they decide are `:ui`'s.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KlasApp"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":ui"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
