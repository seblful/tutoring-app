import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

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

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("KMP_IOS_SIMULATOR_ID").orNull?.let { device.set(it) }
}

// Compile common metadata on JVM builds to catch platform-specific calls on every host.
tasks.named("compileKotlinJvm") {
    dependsOn("compileCommonMainKotlinMetadata")
}
