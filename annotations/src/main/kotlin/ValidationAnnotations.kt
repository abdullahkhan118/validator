package io.github.abdullahkhan118.annotations

/*
 * Constraint annotations for the validator compiler plugin (`compiler-plugin` module).
 *
 * On a @Validated class, the checks are built into the class itself: every constructor validates, and
 * every annotated `var`'s setter checks the new value first, so an invalid object can't be created and an
 * existing one can't be made invalid. The constructors run a generated `private fun validate()`.
 *
 * A failed check throws IllegalArgumentException. A `null` value always passes (use a non-null type to
 * forbid null).
 *
 * The constraints can only be placed on properties (including `val`/`var` constructor parameters). A property's
 * type may be any subtype of the one a constraint applies to (`@Regex` takes any CharSequence). On a collection
 * or array of such values (List, Set, any other Iterable, Array, IntArray, ...), the constraint checks each element.
 */

/** Validates the class with the compiler plugin: on creation (every constructor) and on every update of an annotated `var`. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Validated

/**
 * The property holds a @Validated object (or a collection or array of them), and the owner checks it too: when the
 * owner is created, and when the property is assigned. A failure is reported as
 * `Owner.property is invalid: <the nested object's message>`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class Validate

// Ranges: the value must be within [min, max], both inclusive. Leaving one out means "no limit" on that side.
// (The compiler plugin repeats these defaults; keep them in sync with ConstraintAnnotation in compiler-plugin.)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class ByteRange(val min: Byte = Byte.MIN_VALUE, val max: Byte = Byte.MAX_VALUE)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class ShortRange(val min: Short = Short.MIN_VALUE, val max: Short = Short.MAX_VALUE)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class IntRange(val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class LongRange(val min: Long = Long.MIN_VALUE, val max: Long = Long.MAX_VALUE)

// Defaults are ±infinity: Float.MIN_VALUE / Double.MIN_VALUE are the smallest POSITIVE values,
// which would reject every negative number when only `max` is given.
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class FloatRange(val min: Float = Float.NEGATIVE_INFINITY, val max: Float = Float.POSITIVE_INFINITY)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class DoubleRange(val min: Double = Double.NEGATIVE_INFINITY, val max: Double = Double.POSITIVE_INFINITY)

// Distinct: the value must be one of the listed values.

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class ByteDistinct(vararg val values: Byte)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class ShortDistinct(vararg val values: Short)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class IntDistinct(vararg val values: Int)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class LongDistinct(vararg val values: Long)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class FloatDistinct(vararg val values: Float)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class DoubleDistinct(vararg val values: Double)

@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class StringDistinct(vararg val values: String)

/** The whole value (any CharSequence) must match [pattern]. The pattern's syntax is checked at compile time. */
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.BINARY)
annotation class Regex(val pattern: String)
