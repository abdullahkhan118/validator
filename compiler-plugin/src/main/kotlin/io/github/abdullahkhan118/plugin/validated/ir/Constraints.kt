package io.github.abdullahkhan118.plugin.validated.ir

import io.github.abdullahkhan118.plugin.validated.ConstraintAnnotation
import io.github.abdullahkhan118.plugin.validated.ConstraintKind
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.properties
import java.util.regex.PatternSyntaxException

/*
 * Reading the constraint annotations of a class, and rejecting wrong uses at compile time.
 *
 * In IR an annotation is a constructor call, e.g. `@IntRange(18, 65)` is `IntRange(min = 18, max = 65)`
 * with constant arguments. A `vararg` argument (`@IntDistinct(1, 2, 3)`) is an IrVararg holding the constants.
 * Arguments left out are null; for ranges they fall back to ConstraintAnnotation.defaultMin/defaultMax.
 */

/** One rule on one property, read from one annotation. Values are plain Kotlin values (Byte, Int, String, ...). */
sealed interface Constraint {
    val annotation: ConstraintAnnotation

    /** `min..max`, inclusive; null means "no limit" on that side. */
    data class Range(override val annotation: ConstraintAnnotation, val min: Any?, val max: Any?) : Constraint

    /** The value must equal one of [values]. */
    data class Distinct(override val annotation: ConstraintAnnotation, val values: List<Any>) : Constraint

    /** The whole value must match [pattern]. */
    data class Pattern(override val annotation: ConstraintAnnotation, val pattern: String) : Constraint
}

/**
 * A property with at least one constraint.
 *
 * @property type the property's type, e.g. `Int` or `String?`. A nullable property accepts null.
 */
class ValidatedProperty(val property: IrProperty, val type: IrType, val constraints: List<Constraint>)

/**
 * The result of reading a class: its constrained properties, and whether any annotation was used wrongly
 * (those were already reported as compile errors).
 */
class ClassConstraints(val properties: List<ValidatedProperty>, val hasErrors: Boolean)

/** Reads constraints and reports misuse through [messages], pointing at the annotation in the source. */
class ConstraintReader(private val messages: MessageCollector) {
    private var hasErrors = false

    /** Every property declared in [irClass] (not inherited) that has a constraint annotation. */
    fun read(irClass: IrClass): ClassConstraints {
        hasErrors = false
        val properties = irClass.properties.filter { !it.isFakeOverride }.mapNotNull { property ->
            val type = property.getter?.returnType ?: property.backingField?.type ?: return@mapNotNull null
            val constraints = property.annotations.mapNotNull { annotation -> readConstraint(irClass, property, type, annotation) }
            if (constraints.isEmpty()) null else ValidatedProperty(property, type, constraints)
        }.toList()
        return ClassConstraints(properties, hasErrors)
    }

    /** Reports an error at [element] in [irClass]'s file, as `file:line:column: message`. */
    fun error(irClass: IrClass, element: IrElement, message: String) {
        hasErrors = true
        report(CompilerMessageSeverity.ERROR, irClass, element, message)
    }

    /** Reports a warning at [element]: the build continues. */
    fun warning(irClass: IrClass, element: IrElement, message: String) =
        report(CompilerMessageSeverity.WARNING, irClass, element, message)

    private fun report(severity: CompilerMessageSeverity, irClass: IrClass, element: IrElement, message: String) {
        val file = irClass.file
        val entry = file.fileEntry
        // Offsets are character positions in the file; the file entry converts them to (0-based) line/column.
        val location = CompilerMessageLocation.create(
            entry.name,
            entry.getLineNumber(element.startOffset) + 1,
            entry.getColumnNumber(element.startOffset) + 1,
            null,
        )
        messages.report(severity, "[validated] $message", location)
    }

    /**
     * The constraint described by [annotation], or null if it isn't a constraint annotation, or is used wrongly
     * (then an error was reported).
     */
    private fun readConstraint(irClass: IrClass, property: IrProperty, type: IrType, annotation: IrConstructorCall): Constraint? {
        val constraint = ConstraintAnnotation.byFqName(annotation.type.classFqName?.asString()) ?: return null
        val where = "${irClass.name}.${property.name}"
        val annotationName = "@${constraint.simpleName}"

        // The annotation's type must match the property's (ignoring `?`): @IntRange only on Int, etc.
        val propertyType = type.makeNotNull().classFqName?.asString()
        if (propertyType != constraint.valueType.fqName) {
            error(irClass, annotation, "$annotationName can only be used on a ${constraint.valueType.fqName} property, but $where is $propertyType")
            return null
        }

        val arguments = annotation.argumentsByName()
        return when (constraint.kind) {
            ConstraintKind.RANGE -> {
                val min = arguments["min"]?.constValue() ?: constraint.defaultMin
                val max = arguments["max"]?.constValue() ?: constraint.defaultMax
                if (compare(min, max) > 0) {
                    error(irClass, annotation, "$annotationName on $where has min ($min) greater than max ($max)")
                    return null
                }
                // A side left at its default is no limit at all, so it's not checked.
                Constraint.Range(
                    constraint,
                    min = min.takeIf { it != constraint.defaultMin },
                    max = max.takeIf { it != constraint.defaultMax },
                )
            }
            ConstraintKind.DISTINCT -> {
                val values = arguments["values"]?.varargValues().orEmpty()
                if (values.isEmpty()) {
                    error(irClass, annotation, "$annotationName on $where lists no allowed values")
                    return null
                }
                Constraint.Distinct(constraint, values)
            }
            ConstraintKind.REGEX -> {
                val pattern = arguments["pattern"]?.constValue() as? String
                if (pattern.isNullOrEmpty()) {
                    error(irClass, annotation, "$annotationName on $where has an empty pattern")
                    return null
                }
                // Compile it now, so a broken pattern fails the build instead of every object creation.
                try {
                    Regex(pattern)
                } catch (e: PatternSyntaxException) {
                    error(irClass, annotation, "$annotationName on $where has an invalid pattern: ${e.description} near index ${e.index}")
                    return null
                }
                Constraint.Pattern(constraint, pattern)
            }
        }
    }

    /** The annotation's arguments by parameter name, e.g. {"min" -> 18, "max" -> 65}. Left-out ones are null. */
    private fun IrConstructorCall.argumentsByName(): Map<String, IrExpression?> {
        val parameters = symbol.owner.parameters
        return parameters.indices.associate { parameters[it].name.asString() to arguments[it] }
    }

    /** The value of a constant argument, e.g. 18 or "abc". */
    private fun IrExpression.constValue(): Any? = (this as? IrConst)?.value

    /** The values of a vararg argument, e.g. [1, 2, 3] for `@IntDistinct(1, 2, 3)`. */
    private fun IrExpression.varargValues(): List<Any> = when (this) {
        is IrVararg -> elements.mapNotNull { (it as? IrConst)?.value }
        is IrConst -> listOfNotNull(value)
        else -> emptyList()
    }

    /** Compares two values of the same type (both Int, both Double, ...). */
    @Suppress("UNCHECKED_CAST")
    private fun compare(a: Any?, b: Any?): Int = (a as Comparable<Any>).compareTo(b as Any)
}
