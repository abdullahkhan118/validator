package io.github.abdullahkhan118.plugin.util.fir

/*
 * Descriptions of members a FIR generator adds to a class, turned into real declarations by
 * BaseFirDeclarationGenerationExtension.createFunction / createProperty.
 */

/**
 * Description of one member function to generate.
 *
 * Types are fully qualified class names, with a trailing `?` for nullable: "kotlin.String", "kotlin.Int?",
 * "com.example.User". Only non-generic, top-level classes are supported (no `List<Int>`, no nested
 * classes such as `Outer.Inner`), see `toConeType` in FirUtils.kt.
 *
 * @property name the function's name, e.g. "asString".
 * @property returnType what it returns, e.g. "kotlin.String".
 * @property parameters its parameters, in order; empty for `fun f()`.
 * @property isOpen `open` (subclasses may override it) if true, `final` otherwise. Only meaningful in a
 *   class that can itself be subclassed.
 */
data class GeneratedFunction(
    val name: String,
    val returnType: String,
    val parameters: List<Parameter> = emptyList(),
    val isOpen: Boolean = false,
) {
    /** One parameter, e.g. `Parameter("indent", "kotlin.Int")` for `indent: Int`. */
    data class Parameter(val name: String, val type: String)

    /** Just the parameter types, in order: the form `declaredFunctions` (FirUtils.kt) compares against. */
    val parameterTypes: List<String> get() = parameters.map { it.type }
}

/**
 * Description of one member property (variable) to generate, e.g. `GeneratedProperty("count", "kotlin.Int", isVar = true)`
 * for `var count: Int`. Types are written as in [GeneratedFunction].
 *
 * @property name the property's name.
 * @property type its type, e.g. "kotlin.String?".
 * @property isVar `var` (reassignable) if true, `val` otherwise.
 */
data class GeneratedProperty(
    val name: String,
    val type: String,
    val isVar: Boolean = false,
)
