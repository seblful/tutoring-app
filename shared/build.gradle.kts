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
        compileSdk = 37
        minSdk = 24
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KlasShared"
            isStatic = true
            export(project(":domain"))
        }
    }

    // Fixtures and tests set lessons up without an undo window; app code goes through LessonChanges.
    targets.configureEach {
        compilations.configureEach {
            if (name != "main") {
                compileTaskProvider.configure {
                    compilerOptions.optIn.add("com.seblful.klas.data.lessons.WritesWithoutUndo")
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
        commonTest {
            kotlin.srcDir("src/testFixtures/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
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
