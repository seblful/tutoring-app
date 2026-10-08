import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The domain layer: billing, scheduling, reminders, reports — the app's rules, as plain Kotlin. It is
// its own module so that "no Android in the domain" is something the compiler refuses rather than a
// convention to remember: there is no android.jar on this classpath, so an `android.*` import does not
// compile. Only the JDK (`java.time`, `java.util`) and the Kotlin standard library are available.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Bytecode level matches :app (see its compileOptions/compilerOptions). java.time is fine here: it is
// the app's build that desugars it down to minSdk 24, and that applies to this module's classes too.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
