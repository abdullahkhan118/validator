package io.github.abdullahkhan118.plugin

import io.github.abdullahkhan118.plugin.validated.fir.ValidatorFirExtensionRegistrar
import io.github.abdullahkhan118.plugin.validated.ir.ValidatedIrExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/*
 * Entry point of the validator compiler plugin. The compiler finds this class through
 * META-INF/services (Java ServiceLoader) and calls registerExtensions() once per compilation.
 *
 *     source ──► FIR (understand + check) ──► IR (generate code) ──► JVM bytecode
 *                 ▲ ValidatorFirExtensionRegistrar    ▲ ValidatedIrExtension
 *
 * Package layout:
 *     plugin                 this registrar
 *     plugin.validated       @Validated: names, key and the table of constraint annotations
 *     plugin.validated.fir     declares validate()                        (runs first)
 *     plugin.validated.ir      builds the checks, wires them into constructors and setters
 *     plugin.util.fir        reusable FIR helpers (BaseFirDeclarationGenerationExtension, ...)
 *     plugin.util.ir         reusable IR helpers (addCallAtStart, ...)
 */
@OptIn(ExperimentalCompilerApi::class)
class ValidatorCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId = "io.github.abdullahkhan118.validator"

    // K2 = the Kotlin 2.x compiler frontend. Must be true or the plugin is ignored.
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // The compiler's logger: errors/warnings sent here show up in the build output (and fail the build).
        val messages = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY) ?: MessageCollector.NONE
        FirExtensionRegistrarAdapter.registerExtension(ValidatorFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(ValidatedIrExtension(messages))
    }
}
