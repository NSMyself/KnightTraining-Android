// Domain models + persistence. Plain Kotlin/JVM, no Android, the way the iOS `Core` package has no
// SwiftUI: it keeps the rules testable on the JVM in seconds, without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// The bundled SQLite loads a native library; newer JDKs warn unless that's allowed explicitly.
tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    api(libs.room.runtime)
    api(libs.sqlite.bundled)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
