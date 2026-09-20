plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.github.abdullahkhan118"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":annotations"))
    implementation(libs.ksp.symbol.processing.api)
}