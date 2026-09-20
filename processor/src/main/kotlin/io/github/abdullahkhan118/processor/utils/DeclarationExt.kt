package io.github.abdullahkhan118.processor.utils

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

fun KSDeclaration.getModifiers() = modifiers.map { it.name.lowercase() }.joinToString(" ")

fun KSDeclaration.getDeclaredName() = simpleName.asString()
fun KSDeclaration.getQualifiedName() = qualifiedName?.asString() ?: packageName.asString().plus(simpleName.asString())