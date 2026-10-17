plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "validator"
include("annotations")
include("compiler-plugin")
include("gradle-plugin")
include("app")