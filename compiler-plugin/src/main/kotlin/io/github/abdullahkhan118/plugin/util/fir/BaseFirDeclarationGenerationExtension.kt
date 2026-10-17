package io.github.abdullahkhan118.plugin.util.fir

import io.github.abdullahkhan118.plugin.util.fir.GeneratedFunction
import io.github.abdullahkhan118.plugin.util.fir.GeneratedProperty
import io.github.abdullahkhan118.plugin.util.fir.declaredFunctions as declaredFunctionsWithSignature
import io.github.abdullahkhan118.plugin.util.fir.fqNameString
import io.github.abdullahkhan118.plugin.util.fir.hasAnnotation
import io.github.abdullahkhan118.plugin.util.fir.toConeType
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.name.Name

/*
 * Base class for FIR generators: everything a generator typically needs to decide WHAT to add to a
 * class (checks on the class, its functions, its properties) and to ADD it (functions, properties).
 *
 *     class MyGenerator(session: FirSession) : BaseFirDeclarationGenerationExtension(session) {
 *         override fun getCallableNamesForClass(classSymbol, context) =
 *             if (isDataClass(classSymbol) && !hasFunction(classSymbol, "describe")) setOf(Name.identifier("describe"))
 *             else emptySet()
 *
 *         override fun generateFunctions(callableId, context) =
 *             listOf(createFunction(context!!.owner, MyKey, GeneratedFunction("describe", "kotlin.String")))
 *     }
 *
 * The one rule behind every check here: SAFE TO CALL FROM INSIDE A GENERATOR
 * --------------------------------------------------------------------------
 * The obvious APIs for "what does this class contain", `processAllDeclaredCallables` and
 * `processAllDeclarations`, go through the class's *declared member scope*. Building that scope asks
 * every generator for its members, i.e. calls `getCallableNamesForClass` again, which calls the check
 * again, ... -> StackOverflowError. So every check here reads the class's raw `declarations` list
 * (what's written in its source) instead, which never involves generators.
 *
 * Consequences, true for every check:
 *   - Only the class's OWN declarations are seen: what's written in its source, plus the members the
 *     compiler adds while reading it (a data class's `componentN()` and `copy()`, and the default
 *     constructor of a class or object that doesn't write one). Not inherited members, and not
 *     plugin-generated ones (so a generator never "finds" what it generates itself).
 *   - Class modifiers are read as written (`rawStatus`), because the compiler may not have worked out
 *     the final ones yet. E.g. a member of an interface is abstract without saying so; see [isAbstract].
 *   - Types are compared as fully qualified names ("kotlin.Int?"), see GeneratedFunction. A type that
 *     isn't resolved yet doesn't match anything; in practice types are resolved by `generateFunctions`
 *     / `generateProperties` (round 2), and early enough for no-parameter functions in round 1.
 *
 * Opt-ins used throughout: `.fir` ([SymbolInternals]) gets the class node from its symbol, and reading
 * `declarations` ([DirectDeclarationsAccess]) is complete only for classes compiled from source,
 * which is the case for every class a generator adds members to.
 */
@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
abstract class BaseFirDeclarationGenerationExtension(session: FirSession) :
    FirDeclarationGenerationExtension(session) {

    // ---------------------------------------------------------------------------------------------
    // Class checks
    // ---------------------------------------------------------------------------------------------

    /**
     * True if the class is annotated with [annotationName] (fully qualified), e.g.
     * `hasAnnotation(classSymbol, "io.github.abdullahkhan118.annotations.Validated")`.
     *
     * The annotation must be registered as a predicate in `registerPredicates()`, or this is always false
     * (see `hasAnnotation` in FirUtils.kt for why).
     */
    fun hasAnnotation(classSymbol: FirClassSymbol<*>, annotationName: String): Boolean =
        classSymbol.hasAnnotation(annotationName, session)

    /** `class X` (including data, value, inner, abstract, open and sealed classes). Not an interface/object/enum. */
    fun isClass(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.classKind == ClassKind.CLASS

    /** `interface X` (including `fun interface X`). */
    fun isInterface(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.classKind == ClassKind.INTERFACE

    /** `object X` or `companion object`. Use [isCompanionObject] to tell them apart. */
    fun isObject(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.classKind == ClassKind.OBJECT

    /** `enum class X`. (Its entries are separate declarations, not checked here.) */
    fun isEnumClass(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.classKind == ClassKind.ENUM_CLASS

    /** `annotation class X`. */
    fun isAnnotationClass(classSymbol: FirClassSymbol<*>): Boolean =
        classSymbol.classKind == ClassKind.ANNOTATION_CLASS

    /** `data class X` or `data object X`. */
    fun isDataClass(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.rawStatus.isData

    /** `companion object` inside another class. */
    fun isCompanionObject(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.rawStatus.isCompanion

    /** `inner class X`: a nested class that holds a reference to its outer class's instance. */
    fun isInnerClass(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.rawStatus.isInner

    /** `value class X` (or the older `inline class X`). */
    fun isValueClass(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.rawStatus.isValue

    /**
     * Can't be instantiated directly: `abstract class`, `sealed class`, or any interface.
     * (Modality is read as written, so interfaces are checked by kind: they're abstract without saying so.)
     */
    fun isAbstract(classSymbol: FirClassSymbol<*>): Boolean =
        isInterface(classSymbol) || classSymbol.rawStatus.modality in setOf(Modality.ABSTRACT, Modality.SEALED)

    /** `sealed class X` or `sealed interface X`. */
    fun isSealed(classSymbol: FirClassSymbol<*>): Boolean = classSymbol.rawStatus.modality == Modality.SEALED

    /** Can be subclassed: `open`, `abstract` or `sealed` class, or an interface. Kotlin classes are final by default. */
    fun isOpen(classSymbol: FirClassSymbol<*>): Boolean =
        isInterface(classSymbol) ||
            classSymbol.rawStatus.modality in setOf(Modality.OPEN, Modality.ABSTRACT, Modality.SEALED)

    /**
     * Can get members with bodies that use `this`: a class or object (not an interface, enum or annotation).
     * A good default filter before generating members that an IR extension will give a body.
     */
    fun canHaveGeneratedMembers(classSymbol: FirClassSymbol<*>): Boolean =
        isClass(classSymbol) || isObject(classSymbol)

    // ---------------------------------------------------------------------------------------------
    // Function checks
    // ---------------------------------------------------------------------------------------------

    /**
     * Every function the class declares itself, including extension functions declared inside it.
     *
     * For a data class this includes the compiler-made `component1()`, `component2()`, ... and `copy()`
     * (but not `equals`/`hashCode`/`toString`, which are added later). That's what you want for clash
     * checks: generating a `copy()` on a data class would clash with the compiler's.
     *
     * Constructors aren't functions here; see [declaredConstructors].
     */
    fun declaredFunctions(classSymbol: FirClassSymbol<*>): List<FirNamedFunctionSymbol> =
        classSymbol.fir.declarations.filterIsInstance<FirNamedFunction>().map { it.symbol }

    /** Every overload of [name] written in the class's source, e.g. both `f()` and `f(x: Int)`. */
    fun declaredFunctions(classSymbol: FirClassSymbol<*>, name: String): List<FirNamedFunctionSymbol> =
        declaredFunctions(classSymbol).filter { it.name.asString() == name }

    /**
     * True if the class declares a function with this exact signature:
     *   - same name,
     *   - not an extension function (`fun Other.f()` declared inside the class),
     *   - same number of parameters, with the same type at each position, nullability included
     *     ("kotlin.Int?" != "kotlin.Int"). Parameter names don't matter, as in Kotlin overloading.
     *
     *     hasFunction(classSymbol, "asString")                            // fun asString()
     *     hasFunction(classSymbol, "save", "kotlin.Int", "kotlin.String?") // fun save(a: Int, b: String?)
     *
     * Use it to skip generating a function the user already wrote, which would otherwise clash with it.
     */
    fun hasFunction(classSymbol: FirClassSymbol<*>, name: String, vararg parameterTypes: String): Boolean =
        classSymbol.fir.declaredFunctionsWithSignature(name, *parameterTypes).isNotEmpty()

    /** [hasFunction] for the signature described by [generatedFunction] (its name and parameter types). */
    fun hasFunction(classSymbol: FirClassSymbol<*>, generatedFunction: GeneratedFunction): Boolean =
        hasFunction(classSymbol, generatedFunction.name, *generatedFunction.parameterTypes.toTypedArray())

    /** True if the class declares any function called [name], whatever its parameters. */
    fun hasFunctionNamed(classSymbol: FirClassSymbol<*>, name: String): Boolean =
        declaredFunctions(classSymbol, name).isNotEmpty()

    /**
     * The fully qualified parameter types of [function], in order, e.g. ["kotlin.Int", "kotlin.String?"].
     * An entry is null for a type that isn't resolved yet, or a type parameter such as `T`.
     */
    fun parameterTypes(function: FirNamedFunctionSymbol): List<String?> =
        function.fir.valueParameters.map { it.returnTypeRef.fqNameStringOrNull() }

    /** The fully qualified return type of [function], or null if it isn't resolved yet (or is a `T`). */
    fun returnType(function: FirNamedFunctionSymbol): String? = function.fir.returnTypeRef.fqNameStringOrNull()

    // ---------------------------------------------------------------------------------------------
    // Constructor checks
    // ---------------------------------------------------------------------------------------------

    /**
     * Every constructor of the class: the primary one and any `constructor(...)`.
     * A class or object that doesn't write one still has the default (no-parameter) primary constructor,
     * so this is never empty for them; it's empty for interfaces.
     */
    fun declaredConstructors(classSymbol: FirClassSymbol<*>): List<FirConstructorSymbol> =
        classSymbol.fir.declarations.filterIsInstance<FirConstructor>().map { it.symbol }

    /**
     * The primary constructor: the parameter list right after the class name, `class User(val name: String)`.
     * For a class that doesn't write one (`class X`, `object X`), the default no-parameter constructor.
     * Null if there's none: an interface, or a class with only secondary `constructor(...)`s.
     */
    fun primaryConstructor(classSymbol: FirClassSymbol<*>): FirConstructorSymbol? =
        classSymbol.fir.declarations.filterIsInstance<FirConstructor>().firstOrNull { it.isPrimary }?.symbol

    /**
     * The primary constructor's parameters, in order, e.g. `name` and `age` for `class User(val name: String, age: Int)`.
     * Includes plain parameters (`age`), not only `val`/`var` ones; use [isConstructorProperty] to tell.
     * Empty if there's no primary constructor.
     */
    fun primaryConstructorParameters(classSymbol: FirClassSymbol<*>): List<FirValueParameterSymbol> =
        primaryConstructor(classSymbol)?.fir?.valueParameters?.map { it.symbol }.orEmpty()

    // ---------------------------------------------------------------------------------------------
    // Property (variable) checks
    // ---------------------------------------------------------------------------------------------

    /**
     * Every property written in the class's source: `val`/`var` in the primary constructor and in the body,
     * including extension properties declared inside it.
     */
    fun declaredProperties(classSymbol: FirClassSymbol<*>): List<FirPropertySymbol> =
        classSymbol.fir.declarations.filterIsInstance<FirProperty>().map { it.symbol }

    /** The class's property called [name] (not an extension property), or null. */
    fun declaredProperty(classSymbol: FirClassSymbol<*>, name: String): FirPropertySymbol? =
        declaredProperties(classSymbol).firstOrNull {
            it.name.asString() == name && it.fir.receiverParameter == null
        }

    /**
     * True if the class declares a property called [name] (not an extension property).
     *
     * Only the name matters: unlike functions, two properties with the same name always clash, whatever
     * their types. That makes this the right check before generating a property. To also require a type,
     * pass [type] (fully qualified, e.g. "kotlin.Int?").
     */
    fun hasProperty(classSymbol: FirClassSymbol<*>, name: String, type: String? = null): Boolean {
        val property = declaredProperty(classSymbol, name) ?: return false
        return type == null || propertyType(property) == type
    }

    /** [hasProperty] by the name of [generatedProperty] (its type doesn't matter for a clash). */
    fun hasProperty(classSymbol: FirClassSymbol<*>, generatedProperty: GeneratedProperty): Boolean =
        hasProperty(classSymbol, generatedProperty.name)

    /** The fully qualified type of [property], e.g. "kotlin.String?". Null if not resolved yet (or a `T`). */
    fun propertyType(property: FirPropertySymbol): String? = property.fir.returnTypeRef.fqNameStringOrNull()

    /** `var` (can be reassigned) rather than `val`. */
    fun isMutable(property: FirPropertySymbol): Boolean = property.fir.isVar

    /** `lateinit var`: has no value until it's first assigned. */
    fun isLateInit(property: FirPropertySymbol): Boolean = property.fir.status.isLateInit

    /** `const val`: a compile-time constant (only allowed in objects and at top level). */
    fun isConst(property: FirPropertySymbol): Boolean = property.fir.status.isConst

    /** `val x by lazy { ... }` or any other `by` delegate. */
    fun isDelegated(property: FirPropertySymbol): Boolean = property.fir.delegate != null

    /**
     * Stores a value in the object (a *backing field*), like `val x = 1` or a constructor `val`.
     * False for computed properties, `val x get() = ...`, which run their getter every time instead.
     * (The compiler's own check: a custom getter/setter that never uses `field` means no field.)
     */
    fun hasBackingField(property: FirPropertySymbol): Boolean = property.fir.hasBackingField

    /** Declared as `val`/`var` in the primary constructor, e.g. `name` in `class User(val name: String)`. */
    fun isConstructorProperty(property: FirPropertySymbol): Boolean =
        property.fir.correspondingValueParameterFromPrimaryConstructor != null

    // ---------------------------------------------------------------------------------------------
    // Adding functions and properties (for generateFunctions / generateProperties)
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds the member function described by [function] inside [owner]: public, final (or open, see
     * [GeneratedFunction.isOpen]), with the given parameters (in order) and return type, and NO BODY. An IR extension must find it by [key] and
     * write the body, or the backend fails.
     *
     * Call it from `generateFunctions`, and only for names returned from `getCallableNamesForClass`.
     *
     * @param owner the class it belongs to: `context.owner` in `generateFunctions`.
     * @param key tag the IR extension uses to recognise it. Use one key per feature.
     */
    fun createFunction(
        owner: FirClassSymbol<*>,
        key: GeneratedDeclarationKey,
        function: GeneratedFunction,
    ): FirNamedFunctionSymbol =
        createMemberFunction(owner, key, Name.identifier(function.name), function.returnType.toConeType()) {
            modality = if (function.isOpen) Modality.OPEN else Modality.FINAL
            function.parameters.forEach { parameter ->
                valueParameter(Name.identifier(parameter.name), parameter.type.toConeType())
            }
        }.symbol

    /**
     * Builds the member property described by [property] inside [owner]: public, final, `val` or `var`,
     * with a backing field and default getter (and setter for `var`).
     *
     * It has no initializer, so its field starts as the type's default (null, 0, false). To give it a
     * real starting value, an IR extension can find it by [key] and set the field's initializer.
     *
     * Call it from `generateProperties`, and only for names returned from `getCallableNamesForClass`
     * (properties and functions share that list).
     *
     * @param owner the class it belongs to: `context.owner` in `generateProperties`.
     * @param key tag the IR extension uses to recognise it. Use one key per feature.
     */
    fun createProperty(
        owner: FirClassSymbol<*>,
        key: GeneratedDeclarationKey,
        property: GeneratedProperty,
    ): FirPropertySymbol =
        createMemberProperty(
            owner,
            key,
            Name.identifier(property.name),
            property.type.toConeType(),
            isVal = !property.isVar,
            hasBackingField = true,
        ).symbol

    /** A type reference as a fully qualified name, or null if it isn't resolved yet (or is a `T`). */
    private fun FirTypeRef.fqNameStringOrNull(): String? = coneTypeOrNull?.fqNameString()
}
