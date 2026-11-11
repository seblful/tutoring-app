// Build plugins of this repository. `klas.strings` turns Android-format string XML into the typed text
// catalog `:ui` reads on every platform (see docs/architecture.md, «Text»).
plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("strings") {
            id = "klas.strings"
            implementationClass = "com.seblful.klas.gradle.strings.StringsPlugin"
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
