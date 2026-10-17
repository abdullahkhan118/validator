package io.github.abdullahkhan118.plugin.validated.ir

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.superTypes
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.substitute

/*
 * Collections: a constraint on a collection or array property applies to each of its elements, e.g.
 * `@IntRange(1, 5) val scores: List<Int>` checks every score.
 *
 * "Collection" means any subtype of Iterable (List, Set, ArrayList, a user's own `class Scores : ArrayList<Int>()`,
 * ...), `Array<T>`, or a primitive array (IntArray, ...). The element type is found by walking up the type's
 * supertypes to Iterable<E>, substituting type arguments on the way: `class Scores : ArrayList<Int>()` is an
 * ArrayList<Int>, which is a List<Int>, ..., which is an Iterable<Int>, so its elements are Ints.
 */

/**
 * How to go over the elements of a collection-typed property.
 *
 * @property type the element type, e.g. `Int` for `List<Int>`, `Int?` for `Array<Int?>`.
 * @property iterator the `iterator()` to call on the property's value: `Iterable.iterator()`, `Array.iterator()`
 *   or e.g. `IntArray.iterator()`.
 */
class Elements(val type: IrType, val iterator: IrSimpleFunctionSymbol)

/** The elements of a value of [type] (ignoring its `?`), or null if it isn't an array or an Iterable. */
fun IrBuiltIns.elementsOf(type: IrType): Elements? {
    val nonNull = type.makeNotNull() as? IrSimpleType ?: return null
    val arrayClass = nonNull.classOrNull

    if (arrayClass != null) {
        primitiveArrayElementTypes[arrayClass]?.let { elementType -> return Elements(elementType, arrayClass.iterator()) }
    }
    if (arrayClass == this.arrayClass) return Elements(elementType(nonNull), this.arrayClass.iterator())

    val iterable = nonNull.findSupertype(iterableClass) ?: return null
    return Elements(elementType(iterable), iterableClass.iterator())
}

/** The class's `iterator()` function. */
private fun IrClassSymbol.iterator(): IrSimpleFunctionSymbol =
    functions.first { it.owner.name.asString() == "iterator" && it.owner.parameters.size == 1 }

/** The single type argument of `Array<E>` / `Iterable<E>`; `Any?` for a star projection (`List<*>`). */
private fun IrBuiltIns.elementType(type: IrSimpleType): IrType = type.arguments.firstOrNull()?.typeOrNull ?: anyNType

/**
 * This type seen as [target], with type arguments filled in, e.g. `ArrayList<Int>` as Iterable is
 * `Iterable<Int>`. Null if it isn't a subtype of [target].
 */
private fun IrType.findSupertype(target: IrClassSymbol): IrSimpleType? {
    val simple = this as? IrSimpleType ?: return null
    if (simple.classifier == target) return simple
    // Supertypes are written in terms of the class's own type parameters (ArrayList<E> : MutableList<E>);
    // replace those with this type's arguments (E -> Int). A type parameter's supertypes are its bounds.
    val parameters = (simple.classifier as? IrClassSymbol)?.owner?.typeParameters.orEmpty()
    val substitution: Map<IrTypeParameterSymbol, IrType> = parameters.zip(simple.arguments)
        .mapNotNull { (parameter, argument) -> argument.typeOrNull?.let { parameter.symbol to it } }
        .toMap()
    return simple.classifier.superTypes().firstNotNullOfOrNull { it.substitute(substitution).findSupertype(target) }
}
