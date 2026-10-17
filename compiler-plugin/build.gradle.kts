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

    // Tests run the compiler in-process on small sources, with the plugin jar and the annotations.
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(project(":annotations"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    // The tests load the plugin the way a build does: from its jar, via -Xplugin.
    val pluginJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(pluginJar).withPropertyName("validatorPluginJar")
    systemProperty("validator.pluginJar", pluginJar.get().asFile.absolutePath)
}
