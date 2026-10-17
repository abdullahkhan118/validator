package io.github.abdullahkhan118.plugin.validated.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/** Lists the plugin's FIR (frontend) extensions. Registered from ValidatorCompilerPluginRegistrar. */
class ValidatorFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // Declares `validate()` on @Validated classes. `+::X` = "create one per compilation session".
        +::ValidatedFirGenerator
    }
}
