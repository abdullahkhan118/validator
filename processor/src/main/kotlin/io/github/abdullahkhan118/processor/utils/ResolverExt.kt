package io.github.abdullahkhan118.processor.utils

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated

fun Resolver.getSymbolsWithAnnotations(vararg annotationNames: String) = annotationNames.flatMap {
    getSymbolsWithAnnotation(it)
}