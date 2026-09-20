package io.github.abdullahkhan118.processor.providers

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.abdullahkhan118.processor.processors.ValidatorGeneratorProcessor

class ValidatorGeneratorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ValidatorGeneratorProcessor(environment.codeGenerator)
    }
}