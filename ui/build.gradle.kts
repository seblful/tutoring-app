import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

// The app's screens for Android and iOS: Compose Multiplatform UI, view models, navigation, theme, text and
// formatting. Platform hosts (`:app`, `iosApp/`) supply adapters and the window.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    id("klas.strings")
}

val textCatalog = tasks.named<com.seblful.klas.gradle.strings.GenerateTextCatalog>("generateTextCatalog") {
    packageName = "com.seblful.klas.text"
    // `values/` is English: `AppLanguage.FALLBACK`.
    defaultTag = "en"
}

kotlin {
    android {
        namespace = "com.seblful.klas.ui"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        // Compose resources reach the APK as Android assets.
        androidResources { enable = true }
        // commonTest on the JVM, against Android's java.text tables; iOS runs it on the simulator.
        withHostTest {}
    }
    // The iOS app: one static framework holding every module, linked by `iosApp/`.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KlasApp"
            isStatic = true
        }
    }

    compilerOptions {
        // Compose Styles (`ui/theme/KlasStyles.kt`) are still experimental in foundation 1.12.
        optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(textCatalog)
        }
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.components.resources)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material3)
            api(libs.compose.material3.adaptive.navigation.suite)
            api(libs.compose.material3.adaptive.navigation3)
            api(libs.jb.navigation3.ui)
            api(libs.jb.lifecycle.viewmodel.compose)
            api(libs.jb.lifecycle.runtime.compose)
            api(libs.jb.lifecycle.viewmodel.navigation3)
            api(libs.jb.navigationevent.compose)
            api(libs.jb.savedstate.compose)
            api(libs.metrox.viewmodel.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.biometric)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.seblful.klas.resources"
    generateResClass = always
}

composeCompiler {
    // Domain models and kotlinx-datetime values are immutable; see the file.
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("app/compose-stability.conf"))
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("KMP_IOS_SIMULATOR_ID").orNull?.let { device.set(it) }
}
