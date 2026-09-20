package io.github.abdullahkhan118.processor.utils

import com.google.devtools.ksp.symbol.KSValueParameter

fun KSValueParameter.getDeclaredName() = name?.asString().orEmpty()
fun KSValueParameter.getTypeQualifiedName() = type.resolve().declaration.qualifiedName?.asString()
fun KSValueParameter.declareAsVariable() = getDeclaredName()

fun KSValueParameter.hasAnnotation(annotation: Class<*>): Boolean =
    annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotation.canonicalName }

fun KSValueParameter.getConstructorPropertyPrefix() = when {
    isVal -> "val "
    isVar -> "var "
    else -> ""
}
