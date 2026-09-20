package io.github.abdullahkhan118.processor.utils

import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier

fun KSPropertyDeclaration.hasAnnotation(annotation: Class<*>): Boolean =
    annotations.filter { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotation.canonicalName }
        .count() > 0

fun KSPropertyDeclaration.getQualifiedName() =
    qualifiedName?.asString() ?: packageName.asString().plus(simpleName.asString())

fun KSPropertyDeclaration.getTypeQualifiedName() = type.resolve().declaration.qualifiedName?.asString()
fun KSPropertyDeclaration.declareAsVariable() = getDeclaredName()

fun KSPropertyDeclaration.getMutability() = if (isMutable) "var" else "val"