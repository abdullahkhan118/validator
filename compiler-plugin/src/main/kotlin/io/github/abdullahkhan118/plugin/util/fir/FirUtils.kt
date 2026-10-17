package io.github.abdullahkhan118.plugin.util.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/*
 * Small FIR helpers, the frontend counterpart of util/ir/IrUtils.kt.
 * Like there, names and types are fully qualified strings: "kotlin.String", "com.example.User?".
 */

/**
 * True if this declaration is annotated with the annotation [annotationName], e.g.
 * `classSymbol.hasAnnotation("io.github.abdullahkhan118.annotations.Validated", session)`.
 *
 * Uses the compiler's *predicate* index instead of reading the annotations directly. That's what makes
 * it safe to call early, e.g. from a generator's `getCallableNamesForClass`, when annotations may not be
 * resolved yet (reading them directly at that point can miss them or trigger errors).
 *
 * Requirement: some extension of this plugin must register a predicate for this annotation in
 * `registerPredicates()`, e.g. `register(DeclarationPredicate.create { annotated(FqName(name)) })`.
 * The compiler only indexes annotations that were registered up front; for any other annotation
 * this always returns false.
 *
 * @param annotationName fully qualified name of the annotation class.
 * @param session the module being compiled (every FIR extension and checker has one).
 */
fun FirBasedSymbol<*>.hasAnnotation(annotationName: String, session: FirSession): Boolean =
    session.predicateBasedProvider.matches(DeclarationPredicate.create { annotated(FqName(annotationName)) }, this)

/**
 * Turns a fully qualified type name into FIR's type representation, e.g. "kotlin.Int?" -> `Int?`.
 *
 * Only non-generic, top-level classes: `ClassId.topLevel` treats everything before the last dot as the
 * package, so a nested class "com.example.Outer.Inner" would be looked up as class `Inner` in package
 * `com.example.Outer`. A class that doesn't exist becomes an error type, reported where it's used.
 */
fun String.toConeType(): ConeKotlinType {
    val nullable = endsWith("?")
    val classId = ClassId.topLevel(FqName(removeSuffix("?")))
    return classId.constructClassLikeType(emptyArray(), isMarkedNullable = nullable)
}

/**
 * The functions written in this class's source with this exact signature, e.g.
 * `declaredFunctions("validate")` -> every `fun validate()` with no parameters.
 *
 * A function matches when:
 *   - its name is [functionName] (a plain member name, not fully qualified),
 *   - it has exactly as many parameters as [parameterTypes], and the type at each position is the
 *     same (nullability included: "kotlin.Int?" != "kotlin.Int"),
 *   - it isn't an extension function (`fun Other.asString()` declared inside the class).
 *
 * Generic arguments are ignored ("kotlin.collections.List" matches `List<Int>`), and a type parameter
 * such as `T` never matches.
 *
 * Only declarations written in source are returned: plugin-generated functions are kept separately by
 * the compiler. Reading `declarations` needs the [DirectDeclarationsAccess] opt-in because, for classes
 * from libraries, the list can be incomplete; call this only on classes compiled from source.
 *
 * Parameter types are compared using their resolved types. Very early in compilation (e.g. a generator's
 * `getCallableNamesForClass`), they may not be resolved yet, and a function with parameters then doesn't
 * match. Functions with no parameters always can be matched.
 *
 * @param functionName the function's simple name, e.g. "validate".
 * @param parameterTypes fully qualified parameter types, in order; none for a no-argument function.
 */
@OptIn(DirectDeclarationsAccess::class)
fun FirClass.declaredFunctions(functionName: String, vararg parameterTypes: String): List<FirNamedFunction> =
    declarations.filterIsInstance<FirNamedFunction>().filter { function ->
        val parameters = function.valueParameters
        function.name.asString() == functionName &&
            function.receiverParameter == null &&
            parameters.size == parameterTypes.size &&
            parameters.zip(parameterTypes).all { (parameter, expected) ->
                parameter.returnTypeRef.coneTypeOrNull?.fqNameString() == expected
            }
    }

/**
 * A type in the same form used everywhere in these helpers (and in [GeneratedFunction]),
 * e.g. "kotlin.Any?". Generic arguments are dropped. Null for a type parameter such as `T`.
 */
fun ConeKotlinType.fqNameString(): String? {
    val fqName = classId?.asSingleFqName()?.asString() ?: return null
    return if (isMarkedNullable) "$fqName?" else fqName
}
