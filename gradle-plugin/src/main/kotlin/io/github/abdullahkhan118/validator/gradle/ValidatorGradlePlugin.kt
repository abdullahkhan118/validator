package io.github.abdullahkhan118.validator.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/*
 * `plugins { id("io.github.abdullahkhan118.validator") }`: sets a Kotlin/JVM project up for @Validated.
 *
 *  - adds the annotations (`validator-annotations`) to `implementation`,
 *  - loads the compiler plugin (`validator-compiler-plugin`) into every JVM compilation, main and test alike.
 *
 * Both are the version of this plugin. The compiler plugin uses the Kotlin compiler's internal APIs, so it only
 * works with the Kotlin version it was built with: another version fails the build with an explanation, instead
 * of crashing inside the compiler.
 */
class ValidatorGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        // Whichever Kotlin JVM plugin the project uses (applied before or after this one).
        listOf("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.android").forEach { kotlinPluginId ->
            target.plugins.withId(kotlinPluginId) {
                target.dependencies.add("implementation", "${BuildConfig.GROUP}:$ANNOTATIONS_ARTIFACT:${BuildConfig.VERSION}")
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.platformType == KotlinPlatformType.jvm || kotlinCompilation.platformType == KotlinPlatformType.androidJvm

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(BuildConfig.GROUP, COMPILER_PLUGIN_ARTIFACT, BuildConfig.VERSION)

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project
        val kotlinVersion = project.plugins.withType(KotlinBasePlugin::class.java).firstOrNull()?.pluginVersion
        if (kotlinVersion != null && kotlinVersion != BuildConfig.KOTLIN_VERSION) {
            throw GradleException(
                "Validator ${BuildConfig.VERSION} needs Kotlin ${BuildConfig.KOTLIN_VERSION}, but ${project.displayName} uses " +
                    "Kotlin $kotlinVersion. Compiler plugins use the compiler's internal APIs, so the versions must match: " +
                    "use Kotlin ${BuildConfig.KOTLIN_VERSION}, or the Validator version built for Kotlin $kotlinVersion.",
            )
        }
        // The compiler plugin takes no options.
        return project.provider { emptyList() }
    }

    private companion object {
        /** Must match `pluginId` in the compiler plugin's ValidatorCompilerPluginRegistrar. */
        const val COMPILER_PLUGIN_ID = "io.github.abdullahkhan118.validator"
        const val COMPILER_PLUGIN_ARTIFACT = "validator-compiler-plugin"
        const val ANNOTATIONS_ARTIFACT = "validator-annotations"
    }
}
