package io.github.abdullahkhan118.validator.plugin.util.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol

/* Small IR helpers for adding calls to existing function bodies. */

/**
 * Adds a call to [declaredFunction] as the next statement of the block being built, e.g.
 * `addFunctionCall(println, irString("hi"))` adds `println("hi")`.
 *
 * [values] are the call's arguments, in parameter order. Each is an IR expression,
 * e.g. irString("text"), irInt(42), or irGet(someVariable).
 */
fun IrBlockBodyBuilder.addFunctionCall(declaredFunction: IrSimpleFunctionSymbol, vararg values: IrExpression) {
    // `+` appends the call to the block as a statement.
    +irFunctionCall(declaredFunction, *values)
}

/**
 * Creates (but does not add anywhere) a call to [declaredFunction] with [values] as its arguments.
 * Use it where a call is needed as a value, e.g. the `finally` part of a try.
 */
fun IrBuilderWithScope.irFunctionCall(declaredFunction: IrSimpleFunctionSymbol, vararg values: IrExpression): IrCall {
    // irCall creates the call node; it starts with one empty slot per parameter.
    val call = irCall(declaredFunction)
    require(values.size == call.arguments.size) {
        "${declaredFunction.owner.name} expects ${call.arguments.size} argument(s) but got ${values.size}"
    }
    // Fill each slot with the matching value.
    values.forEachIndexed { index, value -> call.arguments[index] = value }
    return call
}

/**
 * Makes `this` function call [declaredFunction] with [values] before anything else in its body:
 *     fun f() { a; b }   ->   fun f() { call(values); a; b }
 *     fun f() = expr     ->   fun f() { call(values); return expr }
 *
 * Returns false (and changes nothing) if the function has no body to change.
 */
fun IrSimpleFunction.addCallAtStart(
    pluginContext: IrPluginContext,
    declaredFunction: IrSimpleFunctionSymbol,
    vararg values: IrExpression,
): Boolean {
    val statements = bodyStatements(pluginContext) ?: return false
    body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
        addFunctionCall(declaredFunction, *values)
        // Re-add every original statement, unchanged, after our call.
        statements.forEach { +it }
    }
    return true
}

/**
 * The function's body as a plain list of statements, whichever shape it has:
 *     { a; b }  ->  [a, b]
 *     = expr    ->  [return expr]   (an expression body is just a value, so it becomes an explicit return)
 * Null if there is no body (abstract/interface/external functions) or it has an unusual shape.
 */
private fun IrSimpleFunction.bodyStatements(pluginContext: IrPluginContext) = when (val body = body) {
    is IrBlockBody -> body.statements.toList()
    is IrExpressionBody -> listOf(DeclarationIrBuilder(pluginContext, symbol).irReturn(body.expression))
    else -> null
}
