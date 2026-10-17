package io.github.abdullahkhan118.plugin.validated.ir

import io.github.abdullahkhan118.plugin.util.ir.addCallAtStart
import io.github.abdullahkhan118.plugin.validated.ValidatedKey
import io.github.abdullahkhan118.plugin.validated.ValidatedNames
import io.github.abdullahkhan118.plugin.validated.ValueType
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.irWhile
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameterWithClassParent
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnnotationClass
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/*
 * IR part of @Validated: builds the checks and makes every way of setting a value go through them.
 *
 * For
 *     @Validated
 *     class User(@IntRange(18, 65) var age: Int, @Regex("[a-z]+") val name: String?)
 *
 * the class ends up as if it had been written
 *
 *     class User(age: Int, name: String?) {
 *         var age: Int = age
 *             set(value) { validateAge(value); field = value }          // (3) setters check first
 *         val name: String? = name
 *
 *         init { ... }                                                  // user's init blocks, unchanged
 *
 *         private fun validate() {                                      // (2) this class's own properties
 *             validateAge(this.age)
 *             validateName(this.name)
 *         }
 *
 *         private fun validateAge(value: Int) {                         // (1) one check per property
 *             if (value < 18 || value > 65) throw IllegalArgumentException("User.age must be between 18 and 65 but was " + value)
 *         }
 *         private fun validateName(value: String?) {
 *             if (value == null) return                                 //     null always passes
 *             if (!Regex("[a-z]+").matches(value)) throw IllegalArgumentException(...)
 *         }
 *     }
 *     // (4) and every constructor ends with `validate()`
 *     //     (for an `object`: a last `init { validate() }`).
 *
 * A constraint on a collection or array (see Elements.kt) checks every element, giving its position:
 *
 *     @IntRange(1, 5) val scores: List<Int>
 *
 *     private fun validateScores(value: List<Int>) {
 *         val iterator = value.iterator()
 *         var index = 0
 *         while (iterator.hasNext()) {
 *             val element = iterator.next()
 *             if (element < 1 || element > 5) throw IllegalArgumentException("User.scores[" + index + "] must be between 1 and 5 but was " + element)
 *             index = index + 1
 *         }
 *     }
 *
 * (A null element passes, like a null property.)
 *
 * validate() is private, so it can only run from the constructors: user code can't call it. Being private, it
 * also isn't virtual, so in a hierarchy each class has its own: a superclass's constructor checks only its own
 * properties and never runs a subclass's checks before the subclass has set its fields. Every class in the
 * hierarchy that is @Validated validates its own properties at the end of its constructor.
 *
 * So an invalid object can't be created (the constructor throws), and an existing one can't be made
 * invalid through a setter (it throws before the field changes).
 */
class ValidatedIrExtension(private val messages: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = ValidationSymbols.find(pluginContext, messages) ?: return
        val reader = ConstraintReader(pluginContext, messages)
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitClass(declaration: IrClass): IrStatement {
                // Nested classes first, then this one (adding functions to it while visiting is fine then).
                super.visitClass(declaration)
                ValidatedClassTransformer(pluginContext, symbols, reader, declaration).transform()
                return declaration
            }
        })
    }
}

/**
 * The library functions the generated checks call. Looked up once per module.
 *
 * @property regexConstructor `kotlin.text.Regex(pattern: String)`.
 * @property regexMatches `Regex.matches(input: CharSequence): Boolean` (whole-input match).
 * @property exceptionConstructor `IllegalArgumentException(message: String)`. Referenced as
 *   `java.lang.IllegalArgumentException`, because `kotlin.IllegalArgumentException` is only a typealias.
 */
class ValidationSymbols(
    val regexConstructor: IrConstructorSymbol,
    val regexMatches: IrSimpleFunctionSymbol,
    val exceptionConstructor: IrConstructorSymbol,
) {
    companion object {
        fun find(pluginContext: IrPluginContext, messages: MessageCollector): ValidationSymbols? {
            val regex = pluginContext.referenceClass(ClassId(FqName("kotlin.text"), Name.identifier("Regex")))
            val exception = pluginContext.referenceClass(ClassId(FqName("java.lang"), Name.identifier("IllegalArgumentException")))
            val regexConstructor = regex?.constructors?.firstOrNull { it.owner.hasSingleStringParameter() }
            val regexMatches = regex?.functions?.firstOrNull {
                it.owner.name.asString() == "matches" && it.owner.parameters.size == 2
            }
            val exceptionConstructor = exception?.constructors?.firstOrNull { it.owner.hasSingleStringParameter() }
            if (regexConstructor == null || regexMatches == null || exceptionConstructor == null) {
                messages.report(CompilerMessageSeverity.ERROR, "[validated] kotlin.text.Regex or IllegalArgumentException not found")
                return null
            }
            return ValidationSymbols(regexConstructor, regexMatches, exceptionConstructor)
        }

        private fun org.jetbrains.kotlin.ir.declarations.IrFunction.hasSingleStringParameter() =
            parameters.size == 1 && parameters[0].type.makeNotNull().classFqName?.asString() == "kotlin.String"
    }
}

/** Applies validation to one class, if it's @Validated. */
private class ValidatedClassTransformer(
    private val pluginContext: IrPluginContext,
    private val symbols: ValidationSymbols,
    private val reader: ConstraintReader,
    private val irClass: IrClass,
) {
    private val irBuiltIns = pluginContext.irBuiltIns

    fun transform() {
        val isValidated = irClass.hasAnnotation(FqName(ValidatedNames.ANNOTATION))
        val constraints = reader.read(irClass)

        // Constraints in a class without @Validated would silently do nothing.
        if (!isValidated) {
            constraints.properties.forEach {
                reader.warning(irClass, it.property, "${irClass.name}.${it.property.name} has constraints, but they have no effect: ${irClass.name} isn't annotated with @Validated")
            }
            return
        }

        if (irClass.isInterface || irClass.isAnnotationClass || irClass.isValue) {
            reader.error(irClass, irClass, "@Validated can only be used on classes and objects, not on ${if (irClass.isValue) "value classes" else "interfaces or annotation classes"}")
            return
        }

        // The generated private validate() would clash with a validate() the class declares, or inherits from a
        // superclass (a "fake override" here; a @Validated superclass's private validate() isn't inherited).
        val existing = irClass.functions.firstOrNull { it.isValidateSignature() }
        if (existing != null) {
            val what = if (existing.isFakeOverride) "inherit" else "declare"
            reader.error(irClass, if (existing.isFakeOverride) irClass else existing, "@Validated generates a private 'validate()', so ${irClass.name} can't $what one. Rename it or remove the @Validated annotation.")
            return
        }

        // On misuse (already reported), generate nothing.
        if (constraints.hasErrors) return

        val validators = constraints.properties.associateWith { buildPropertyValidator(it) }
        val validate = buildValidate(validators)
        if (irClass.kind == ClassKind.OBJECT) validateInLastInitBlock(validate) else validateAtEndOfConstructors(validate)
        validateAtStartOfSetters(validators)
    }

    /** `fun validate()`: that name, and no parameters besides `this`. */
    private fun IrSimpleFunction.isValidateSignature(): Boolean =
        name.asString() == ValidatedNames.VALIDATE && parameters.size == 1 && dispatchReceiverParameter != null

    // ---------------------------------------------------------------------------------------------
    // (1) One private check per property: `private fun validateAge(value: Int)`
    // ---------------------------------------------------------------------------------------------

    private fun buildPropertyValidator(validated: ValidatedProperty): IrSimpleFunction {
        val propertyName = validated.property.name.asString()
        val function = irClass.addFunction {
            name = Name.identifier(ValidatedNames.propertyValidatorName(propertyName))
            returnType = irBuiltIns.unitType
            visibility = DescriptorVisibilities.PRIVATE
            origin = IrDeclarationOrigin.GeneratedByPlugin(ValidatedKey)
        }
        // A member function's first parameter is `this`.
        function.parameters = listOf(function.createDispatchReceiverParameterWithClassParent())
        val value = function.addValueParameter("value", validated.type)

        function.body = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
            if (validated.type.isMarkedNullable()) {
                // if (value == null) return
                +irIfThen(irBuiltIns.unitType, irEqualsNull(irGet(value)), irReturn(irUnit()))
            }
            val where = "${irClass.name}.$propertyName"
            val (onElements, onValue) = validated.constraints.partition { it.onElements }
            // `value` as the type each constraint checks (its `?` removed). A fresh node per use: an IR node can
            // appear in only one place.
            onValue.forEach { constraint ->
                addCheck(constraint, { listOf(irString(where)) }, { irImplicitCast(irGet(value), constraint.type) })
            }
            if (onElements.isNotEmpty()) addElementChecks(validated.elements!!, onElements, where, value)
        }
        return function
    }

    /**
     * Checks every element of the collection or array in [collection] against [constraints]:
     * `while (iterator.hasNext()) { val element = iterator.next(); <checks>; index = index + 1 }`.
     */
    private fun IrStatementsBuilder<*>.addElementChecks(
        elements: Elements,
        constraints: List<Constraint>,
        where: String,
        collection: IrValueParameter,
    ) {
        val iteratorClass = irBuiltIns.iteratorClass
        val hasNext = iteratorClass.functions.first { it.owner.name.asString() == "hasNext" }
        val next = iteratorClass.functions.first { it.owner.name.asString() == "next" }

        val iterator = irTemporary(
            irCall(elements.iterator).apply { arguments[0] = irImplicitCast(irGet(collection), collection.type.makeNotNull()) },
            nameHint = "iterator",
            irType = iteratorClass.typeWith(elements.type),
        )
        val index = irTemporary(irInt(0), nameHint = "index", irType = irBuiltIns.intType, isMutable = true)
        +irWhile().apply {
            condition = irCall(hasNext).apply { arguments[0] = irGet(iterator) }
            body = irBlock {
                // Iterator<T>.next() returns T; typing the call as the element type makes the backend unbox it.
                val element = irTemporary(
                    irCall(next, elements.type).apply { arguments[0] = irGet(iterator) },
                    nameHint = "element",
                )
                val whereAt = { listOf(irString("$where["), irGet(index), irString("]")) }
                val checks = irBlock {
                    constraints.forEach { constraint ->
                        addCheck(constraint, whereAt, { irImplicitCast(irGet(element), constraint.type) })
                    }
                }
                // A null element always passes, like a null property.
                +if (elements.type.isNullable()) irIfThen(irBuiltIns.unitType, irNotEquals(irGet(element), irNull()), checks) else checks
                +irSet(index, irCall(irBuiltIns.intPlusSymbol).apply {
                    arguments[0] = irGet(index)
                    arguments[1] = irInt(1)
                })
            }
        }
    }

    /**
     * Adds the check for one constraint. [where] builds the start of the message (`User.age`, or
     * `User.scores[` + index + `]`); [read] gives the value to check.
     */
    private fun IrStatementsBuilder<*>.addCheck(constraint: Constraint, where: () -> List<IrExpression>, read: () -> IrExpression) {
        when (constraint) {
            is Constraint.Range -> addRangeCheck(constraint, where, read)
            is Constraint.Distinct -> addDistinctCheck(constraint, where, read)
            is Constraint.Pattern -> addPatternCheck(constraint, where, read)
        }
    }

    /** `if (value < min || value > max) throw ...`; a side without a limit isn't checked. */
    private fun IrStatementsBuilder<*>.addRangeCheck(constraint: Constraint.Range, where: () -> List<IrExpression>, read: () -> IrExpression) {
        val valueType = constraint.annotation.valueType
        val type = constraint.type
        val tooSmall = constraint.min?.let { compare(irBuiltIns.lessFunByOperandType, type, read(), constant(valueType, it)) }
        val tooBig = constraint.max?.let { compare(irBuiltIns.greaterFunByOperandType, type, read(), constant(valueType, it)) }
        val invalid = listOfNotNull(tooSmall, tooBig).reduceOrNull { a, b -> or(a, b) } ?: return
        val rule = when {
            constraint.min != null && constraint.max != null -> "be between ${constraint.min} and ${constraint.max}"
            constraint.min != null -> "be at least ${constraint.min}"
            else -> "be at most ${constraint.max}"
        }
        +irIfThen(irBuiltIns.unitType, invalid, throwInvalid(where, rule, read(), quote = false))
    }

    /** `if (!(value == a || value == b || ...)) throw ...` */
    private fun IrStatementsBuilder<*>.addDistinctCheck(constraint: Constraint.Distinct, where: () -> List<IrExpression>, read: () -> IrExpression) {
        val valueType = constraint.annotation.valueType
        val isAllowed = constraint.values
            .map { irEquals(read(), constant(valueType, it)) }
            .reduce { a, b -> or(a, b) }
        val quote = valueType == ValueType.STRING
        val allowed = constraint.values.joinToString(", ", "[", "]") { if (quote) "\"$it\"" else "$it" }
        +irIfThen(irBuiltIns.unitType, irNot(isAllowed), throwInvalid(where, "be one of $allowed", read(), quote))
    }

    /** `if (!Regex(pattern).matches(value)) throw ...` */
    private fun IrStatementsBuilder<*>.addPatternCheck(constraint: Constraint.Pattern, where: () -> List<IrExpression>, read: () -> IrExpression) {
        // A new Regex per check: simple, and avoids a shared field that might not be initialized yet
        // when a setter runs during construction.
        val regex = irCallConstructor(symbols.regexConstructor, emptyList()).apply { arguments[0] = irString(constraint.pattern) }
        val matches = irCall(symbols.regexMatches).apply {
            arguments[0] = regex
            arguments[1] = read()
        }
        +irIfThen(irBuiltIns.unitType, irNot(matches), throwInvalid(where, "match the pattern ${constraint.pattern}", read(), quote = true))
    }

    /** `throw IllegalArgumentException("User.age must <rule> but was <value>")`; [where] gives the `User.age` part. */
    private fun IrBuilderWithScope.throwInvalid(where: () -> List<IrExpression>, rule: String, value: IrExpression, quote: Boolean): IrExpression {
        val message = irConcat().apply {
            arguments.addAll(where())
            arguments.add(irString(" must $rule but was ${if (quote) "\"" else ""}"))
            arguments.add(value)
            if (quote) arguments.add(irString("\""))
        }
        return irThrow(irCallConstructor(symbols.exceptionConstructor, emptyList()).apply { arguments[0] = message })
    }

    /** `a < b` / `a > b` for a primitive type, via the compiler's built-in comparison for that type. */
    private fun IrBuilderWithScope.compare(
        comparisons: Map<org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol, IrSimpleFunctionSymbol>,
        type: IrType,
        a: IrExpression,
        b: IrExpression,
    ): IrExpression = irCall(comparisons.getValue(type.classifierOrFail)).apply {
        arguments[0] = a
        arguments[1] = b
    }

    /** `a || b`: in IR, an `if (a) true else b` marked as `||`. */
    private fun IrBuilderWithScope.or(a: IrExpression, b: IrExpression): IrExpression =
        irIfThenElse(irBuiltIns.booleanType, a, irTrue(), b, IrStatementOrigin.OROR)

    /** A constant of the constraint's type, e.g. the `18` in `value < 18`. */
    private fun IrBuilderWithScope.constant(type: ValueType, value: Any): IrExpression = when (type) {
        ValueType.BYTE -> IrConstImpl.byte(startOffset, endOffset, irBuiltIns.byteType, (value as Number).toByte())
        ValueType.SHORT -> IrConstImpl.short(startOffset, endOffset, irBuiltIns.shortType, (value as Number).toShort())
        ValueType.INT -> IrConstImpl.int(startOffset, endOffset, irBuiltIns.intType, (value as Number).toInt())
        ValueType.LONG -> IrConstImpl.long(startOffset, endOffset, irBuiltIns.longType, (value as Number).toLong())
        ValueType.FLOAT -> IrConstImpl.float(startOffset, endOffset, irBuiltIns.floatType, (value as Number).toFloat())
        ValueType.DOUBLE -> IrConstImpl.double(startOffset, endOffset, irBuiltIns.doubleType, (value as Number).toDouble())
        ValueType.STRING -> irString(value as String)
    }

    // ---------------------------------------------------------------------------------------------
    // (2) private fun validate()
    // ---------------------------------------------------------------------------------------------

    /**
     * `private fun validate()`: checks the current value of each constrained property declared in THIS class.
     * Called only by the constructors (not virtual, see the file header).
     */
    private fun buildValidate(validators: Map<ValidatedProperty, IrSimpleFunction>): IrSimpleFunction {
        val function = irClass.addFunction {
            name = Name.identifier(ValidatedNames.VALIDATE)
            returnType = irBuiltIns.unitType
            visibility = DescriptorVisibilities.PRIVATE
            origin = IrDeclarationOrigin.GeneratedByPlugin(ValidatedKey)
        }
        function.parameters = listOf(function.createDispatchReceiverParameterWithClassParent())
        function.body = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
            val self = function.dispatchReceiverParameter!!
            validators.forEach { (validated, validator) ->
                val property = validated.property
                val field = property.backingField
                val getter = property.getter
                // Read the field directly when there is one (no getter code runs); otherwise call the getter.
                val read: () -> IrExpression = when {
                    field != null -> { { irGetField(irGet(self), field) } }
                    getter != null -> { { irCall(getter.symbol).apply { arguments[0] = irGet(self) } } }
                    else -> return@forEach
                }
                val check = irCall(validator.symbol).apply {
                    arguments[0] = irGet(self)
                    arguments[1] = read()
                }
                // A lateinit property may not be set yet: check it only once it has a value.
                +if (property.isLateinit) irIfThen(irBuiltIns.unitType, irNotEquals(read(), irNull()), check) else check
            }
        }
        return function
    }

    // ---------------------------------------------------------------------------------------------
    // (3) Setters: `set(value) { validateAge(value); <original setter> }`
    // ---------------------------------------------------------------------------------------------

    private fun validateAtStartOfSetters(validators: Map<ValidatedProperty, IrSimpleFunction>) {
        validators.forEach { (validated, validator) ->
            val setter = validated.property.setter ?: return@forEach
            val self = setter.dispatchReceiverParameter ?: return@forEach
            val newValue = setter.parameters.firstOrNull { it.kind == IrParameterKind.Regular } ?: return@forEach
            val builder = DeclarationIrBuilder(pluginContext, setter.symbol)
            setter.addCallAtStart(pluginContext, validator.symbol, builder.irGet(self), builder.irGet(newValue))
            // The JVM backend replaces calls to a PRIVATE property's DEFAULT setter with a direct field write
            // (`rank = 5` inside the class becomes `this.rank = 5`), skipping the setter and our check. It
            // decides by this origin, not by the body, so mark the setter as a normal, user-defined one.
            if (setter.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) setter.origin = IrDeclarationOrigin.DEFINED
        }
    }

    // ---------------------------------------------------------------------------------------------
    // (4) Constructors: `validate()` as the last statement of each one
    // ---------------------------------------------------------------------------------------------

    /**
     * Appends `validate()` to every constructor. At the end of the primary constructor all
     * property initializers and `init` blocks have run, so every value is final. A secondary constructor first
     * runs the one it delegates to (which validates), then its own body, then validates again.
     */
    private fun validateAtEndOfConstructors(validate: IrSimpleFunction) {
        val self: IrValueParameter = irClass.thisReceiver ?: return
        irClass.constructors.forEach { constructor ->
            val body = constructor.body as? IrBlockBody ?: return@forEach
            val builder = DeclarationIrBuilder(pluginContext, constructor.symbol)
            body.statements += builder.irCall(validate.symbol).apply { arguments[0] = builder.irGet(self) }
        }
    }

    /**
     * For an `object`: adds `init { validate() }` as its last declaration.
     *
     * The end of the constructor doesn't work for objects: on the JVM an object's properties become static
     * fields, initialized AFTER its (private) constructor has run, so validating there would see 0/null.
     * `init` blocks, though, run together with the property initializers, in declaration order, so a last
     * `init` block sees every property's initial value.
     */
    private fun validateInLastInitBlock(validate: IrSimpleFunction) {
        val self: IrValueParameter = irClass.thisReceiver ?: return
        val initializer = irClass.factory.createAnonymousInitializer(
            irClass.startOffset,
            irClass.endOffset,
            IrDeclarationOrigin.GeneratedByPlugin(ValidatedKey),
            IrAnonymousInitializerSymbolImpl(irClass.symbol),
            isStatic = false,
        )
        initializer.parent = irClass
        val builder = DeclarationIrBuilder(pluginContext, initializer.symbol)
        initializer.body = builder.irBlockBody {
            +irCall(validate.symbol).apply { arguments[0] = irGet(self) }
        }
        irClass.declarations += initializer
    }
}
