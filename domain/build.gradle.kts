import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Keep domain rules in commonMain so platform APIs cannot enter the shared module.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        // Match :app's bytecode target.
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Domain types are part of this module's API.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Compile common metadata on JVM builds to catch platform-specific calls on every host.
tasks.named("compileKotlinJvm") {
    dependsOn("compileCommonMainKotlinMetadata")
}
