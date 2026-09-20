// app/build.gradle.kts

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

group = "io.github.abdullahkhan118"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":annotations"))
    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}