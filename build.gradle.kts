// Root project: only declares plugin versions for the modules (annotations, compiler-plugin, gradle-plugin, app).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish) apply false
}
