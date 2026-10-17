package io.github.abdullahkhan118.validator.plugin.validated.ir

import io.github.abdullahkhan118.validator.plugin.validated.ConstraintAnnotation
import io.github.abdullahkhan118.validator.plugin.validated.ConstraintKind
import io.github.abdullahkhan118.validator.plugin.validated.ValidatedNames
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
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
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.util.regex.PatternSyntaxException

/*
 * Reading the constraint annotations of a class, and rejecting wrong uses at compile time.
 *
 * In IR an annotation is a constructor call, e.g. `@IntRange(18, 65)` is `IntRange(min = 18, max = 65)`
 * with constant arguments. A `vararg` argument (`@IntDistinct(1, 2, 3)`) is an IrVararg holding the constants.
 * Arguments left out are null; for ranges they fall back to ConstraintAnnotation.defaultMin/defaultMax.
 *
 * Which values a constraint checks is decided by type, subtypes included: the property's own value if its type is
 * a subtype of ConstraintAnnotation.appliesTo (`@Regex` on a String or a StringBuilder), otherwise each element if
 * it's a collection or array of such values (`@IntRange` on a List<Int>, ArrayList<Int>, IntArray, ...; see Elements.kt).
 */

/**
 * One rule on one property, read from one annotation. Values are plain Kotlin values (Byte, Int, String, ...).
 *
 * [type] is what a checked value is treated as (`Int`, or `CharSequence` for `@Regex`), and [onElements] says
 * whether the rule applies to the property's value itself or to each of its elements.
 */
sealed interface Constraint {
    val type: IrType
    val onElements: Boolean

    /** `min..max`, inclusive; null means "no limit" on that side. */
    data class Range(
        val annotation: ConstraintAnnotation,
        override val type: IrType,
        override val onElements: Boolean,
        val min: Any?,
        val max: Any?,
    ) : Constraint

    /** The value must equal one of [values]. */
    data class Distinct(
        val annotation: ConstraintAnnotation,
        override val type: IrType,
        override val onElements: Boolean,
        val values: List<Any>,
    ) : Constraint

    /** The whole value must match [pattern]. */
    data class Pattern(
        val annotation: ConstraintAnnotation,
        override val type: IrType,
        override val onElements: Boolean,
        val pattern: String,
    ) : Constraint

    /**
     * `@Validate`: the value is a @Validated object, checked by calling [validated]'s bridge (see Bridges.kt).
     * [validated] is the property's class, or its nearest @Validated superclass.
     */
    data class Nested(
        override val type: IrType,
        override val onElements: Boolean,
        val validated: IrClass,
    ) : Constraint
}

/**
 * A property with at least one constraint.
 *
 * @property type the property's type, e.g. `Int` or `String?`. A nullable property accepts null.
 * @property elements how to go over its elements, if it's a collection or array (whether or not a constraint uses them).
 */
class ValidatedProperty(val property: IrProperty, val type: IrType, val elements: Elements?, val constraints: List<Constraint>)

/**
 * The result of reading a class: its constrained properties, and whether any annotation was used wrongly
 * (those were already reported as compile errors).
 */
class ClassConstraints(val properties: List<ValidatedProperty>, val hasErrors: Boolean)

/** Reads constraints and reports misuse through [messages], pointing at the annotation in the source. */
class ConstraintReader(private val pluginContext: IrPluginContext, private val messages: MessageCollector) {
    private var hasErrors = false

    /** The class each annotation applies to, looked up once. */
    private val appliesTo = mutableMapOf<ConstraintAnnotation, IrClassSymbol?>()

    /** Every property declared in [irClass] (not inherited) that has a constraint annotation. */
    fun read(irClass: IrClass): ClassConstraints {
        hasErrors = false
        val properties = irClass.properties.filter { !it.isFakeOverride }.mapNotNull { property ->
            val type = property.getter?.returnType ?: property.backingField?.type ?: return@mapNotNull null
            val elements = pluginContext.irBuiltIns.elementsOf(type)
            val constraints = property.annotations.mapNotNull { annotation -> readConstraint(irClass, property, type, elements, annotation) }
            if (constraints.isEmpty()) null else ValidatedProperty(property, type, elements, constraints)
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
    private fun readConstraint(
        irClass: IrClass,
        property: IrProperty,
        type: IrType,
        elements: Elements?,
        annotation: IrConstructorCall,
    ): Constraint? {
        val where = "${irClass.name}.${property.name}"
        val annotationFqName = annotation.type.classFqName?.asString()
        if (annotationFqName == ValidatedNames.VALIDATE_ANNOTATION) return readNested(irClass, where, type, elements, annotation)
        val constraint = ConstraintAnnotation.byFqName(annotationFqName) ?: return null
        val annotationName = "@${constraint.simpleName}"

        // The property's type (ignoring `?`), or else its element type, must be a subtype of the annotation's:
        // @IntRange on an Int or a List<Int>, @Regex on a String, a StringBuilder or a Set<String>, etc.
        val accepted = appliesTo.getOrPut(constraint) {
            pluginContext.referenceClass(ClassId.topLevel(FqName(constraint.appliesTo)))
        }
        if (accepted == null) {
            error(irClass, annotation, "$annotationName: ${constraint.appliesTo} not found")
            return null
        }
        val onElements = when {
            type.makeNotNull().isSubtypeOfClass(accepted) -> false
            elements != null && elements.type.makeNotNull().isSubtypeOfClass(accepted) -> true
            else -> {
                error(irClass, annotation, "$annotationName can only be used on a ${constraint.appliesTo} property, or a collection or array of them, but $where is ${type.describe()}")
                return null
            }
        }
        val checkedType = accepted.defaultType

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
                    checkedType,
                    onElements,
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
                Constraint.Distinct(constraint, checkedType, onElements, values)
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
                Constraint.Pattern(constraint, checkedType, onElements, pattern)
            }
        }
    }

    /**
     * `@Validate`: the property's class (ignoring `?`), or else its element class, must be @Validated, or extend a
     * @Validated class (whose bridge then runs the checks, for the whole object).
     */
    private fun readNested(irClass: IrClass, where: String, type: IrType, elements: Elements?, annotation: IrConstructorCall): Constraint? {
        val validatedClassOf = { valueType: IrType -> valueType.makeNotNull().classOrNull?.owner?.nearestValidated() }
        validatedClassOf(type)?.let { return Constraint.Nested(it.symbol.defaultType, onElements = false, it) }
        elements?.let { validatedClassOf(it.type) }?.let { return Constraint.Nested(it.symbol.defaultType, onElements = true, it) }
        error(irClass, annotation, "@Validate can only be used on a property whose type is a @Validated class, or a collection or array of them, but $where is ${type.describe()}")
        return null
    }

    /** The type as written in messages, e.g. `kotlin.collections.List<Plain>` (`?` and `<root>.` left out). */
    private fun IrType.describe(): String = makeNotNull().render().replace("<root>.", "")

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
