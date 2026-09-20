package io.github.abdullahkhan118.processor.processors

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import io.github.abdullahkhan118.annotations.Validate
import io.github.abdullahkhan118.processor.utils.getSymbolsWithAnnotations
import io.github.abdullahkhan118.processor.visitors.ValidatorGeneratorVisitor

class ValidatorGeneratorProcessor(private val codeGenerator: CodeGenerator) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {

        // Get all classes that have Validate annotation

        resolver.getSymbolsWithAnnotations(
            Validate::class.java.canonicalName
        )
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach {
                // Create validator class for each annotated class
                it.accept(ValidatorGeneratorVisitor(codeGenerator), Unit)

            }

        return emptyList()
    }
}