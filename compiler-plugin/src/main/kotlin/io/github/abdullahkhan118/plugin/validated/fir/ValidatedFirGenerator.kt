package io.github.abdullahkhan118.plugin.validated.fir

import io.github.abdullahkhan118.plugin.util.fir.GeneratedFunction
import io.github.abdullahkhan118.plugin.util.fir.BaseFirDeclarationGenerationExtension
import io.github.abdullahkhan118.plugin.validated.ValidatedKey
import io.github.abdullahkhan118.plugin.validated.ValidatedNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.DeclarationGenerationContext
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/*
 * FIR part of @Validated: DECLARES `fun validate()`, so user code can call `user.validate()` to re-check
 * an object on demand.
 *
 * That's the only thing FIR is needed for. Everything else (the per-property checks, and calling them
 * from constructors and setters) is only used by code the plugin injects, so it's all done in IR
 * (ValidatedIrExtension), where the property types and annotation arguments are fully known.
 *
 * Inheritance: only the TOPMOST @Validated class in a hierarchy gets `validate()` from here, and it's
 * `open` if that class can be subclassed. A @Validated subclass inherits it, and ValidatedIrExtension
 * overrides it there (`super.validate()` + the subclass's own checks). Declaring it again in the subclass
 * would make two unrelated `validate()`s, and the JVM rejects a class that redefines a final method.
 *
 * Skipped (and reported as an error by ValidatedIrExtension) for a class that declares its own `validate()`.
 */
class ValidatedFirGenerator(session: FirSession) : BaseFirDeclarationGenerationExtension(session) {

    /** Puts @Validated in the compiler's annotation index, so `hasAnnotation` can find it. */
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(DeclarationPredicate.create { annotated(FqName(ValidatedNames.ANNOTATION)) })
    }

    /** Round 1: `{validate}` for a topmost @Validated class that doesn't declare its own; nothing otherwise. */
    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: DeclarationGenerationContext.Member,
    ): Set<Name> =
        if (hasAnnotation(classSymbol, ValidatedNames.ANNOTATION) &&
            !hasFunction(classSymbol, ValidatedNames.VALIDATE) &&
            !hasValidatedSuperclass(classSymbol)
        ) setOf(Name.identifier(ValidatedNames.VALIDATE))
        else emptySet()

    /** Round 2: `fun validate()`, open when the class can be subclassed, tagged with [ValidatedKey] for IR. */
    override fun generateFunctions(
        callableId: CallableId,
        context: DeclarationGenerationContext.Member?,
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        if (callableId.callableName.asString() != ValidatedNames.VALIDATE) return emptyList()
        val validate = GeneratedFunction(ValidatedNames.VALIDATE, returnType = "kotlin.Unit", isOpen = isOpen(owner))
        return listOf(createFunction(owner, ValidatedKey, validate))
    }

    /**
     * True if any superclass (direct or further up) is @Validated, i.e. this class already inherits a `validate()`.
     * Only superclasses compiled in this module are recognised: the annotation index covers source code only.
     */
    private fun hasValidatedSuperclass(classSymbol: FirClassSymbol<*>): Boolean =
        classSymbol.resolvedSuperTypes.any { superType ->
            val superClass = (superType as? ConeClassLikeType)?.lookupTag?.toRegularClassSymbol(session) ?: return@any false
            hasAnnotation(superClass, ValidatedNames.ANNOTATION) || hasValidatedSuperclass(superClass)
        }
}
