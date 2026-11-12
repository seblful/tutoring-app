import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
}

kotlin {
    android {
        namespace = "com.seblful.klas.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    // The iOS app links `:ui`'s framework, which carries this module.
    iosArm64()
    iosSimulatorArm64()

    // Tests set lessons and settings up without an undo window or their side effects; app code goes
    // through LessonChanges and SettingsWorkflow.
    targets.configureEach {
        compilations.configureEach {
            if (name != "main") {
                compileTaskProvider.configure {
                    compilerOptions.optIn.add("com.seblful.klas.data.lessons.WritesWithoutUndo")
                    compilerOptions.optIn.add("com.seblful.klas.data.settings.WritesWithoutEffects")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(libs.androidx.room.runtime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.named("compileKotlinJvm") {
    dependsOn("compileCommonMainKotlinMetadata")
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("KMP_IOS_SIMULATOR_ID").orNull?.let { device.set(it) }
}
