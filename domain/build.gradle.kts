import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The domain layer: billing, scheduling, reminders, reports — the app's rules, as plain Kotlin. It is
// its own module so that "no platform in the domain" is something the compiler refuses rather than a
// convention to remember. It is Kotlin Multiplatform, and every rule lives in `commonMain`, which sees
// neither android.jar nor the JDK: an `android.*` or `java.*` import does not compile. What it has is the
// Kotlin standard library and kotlinx-datetime, which is what lets the same rules run on iOS.
//
// The JVM is the target the app runs the rules on today. The iOS targets are declared now because they
// are what make `commonMain` mean something: with the JVM alone, Kotlin compiles `commonMain` as JVM code
// and a `java.*` import goes through. Their binaries need a Mac, so on Windows those tasks are skipped —
// but the shared metadata compilation below still runs, against the common standard library only.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        // Bytecode level matches :app (see its compileOptions/compilerOptions).
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api`: its types are the domain's own vocabulary — every rule takes and returns them.
            api(libs.kotlinx.datetime)
        }
        // JUnit 4 rather than kotlin.test for now: the suite predates the move, and its
        // `assertEquals(message, expected, actual)` order is kotlin.test's reversed.
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

// Every JVM build — the app's included — first compiles `commonMain` as common code, so a JVM-only call in
// the domain fails the build on any machine rather than only on a Mac building for iOS.
tasks.named("compileKotlinJvm") {
    dependsOn("compileCommonMainKotlinMetadata")
}
