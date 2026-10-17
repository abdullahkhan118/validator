plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.github.abdullahkhan118"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // The compiler's API, provided by the compiler itself when it loads the plugin: compile against it,
    // don't bundle it. Same version as the Kotlin plugin, which the plugin's internal APIs must match exactly.
    compileOnly(kotlin("compiler-embeddable"))
}

kotlin {
    jvmToolchain(17)
}
