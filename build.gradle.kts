plugins {
    alias(libs.plugins.kotlin.jvm)
    // build.gradle.kts
    alias(libs.plugins.ksp)
}

group = "io.github.abdullahkhan118"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // build.gradle.kts
//    ksp(libs.moshi.kotlin.codegen)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}