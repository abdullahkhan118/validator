// app/build.gradle.kts

plugins {
    alias(libs.plugins.kotlin.jvm)
    // `./gradlew :app:run` runs Main.kt.
    application
}

group = "io.github.abdullahkhan118"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":annotations"))
}

// The validator compiler plugin, loaded straight from its jar (no Gradle plugin needed).
// Gradle configures projects alphabetically, so make sure :compiler-plugin (and its jar task) exists first.
evaluationDependsOn(":compiler-plugin")
val validatorPluginJar = project(":compiler-plugin").tasks.named<Jar>("jar")

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            validatorPluginJar.flatMap { it.archiveFile }.map { listOf("-Xplugin=${it.asFile.absolutePath}") },
        )
    }
}

// Build the plugin jar first, and declare it as an input: it's only passed as a path above, so without
// this Gradle wouldn't notice when the plugin changes and would reuse classes compiled with the old one.
tasks.named("compileKotlin") {
    dependsOn(validatorPluginJar)
    inputs.files(validatorPluginJar).withPropertyName("validatorPluginJar")
}

application {
    mainClass = "io.github.abdullahkhan118.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
