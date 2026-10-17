package io.github.abdullahkhan118.plugin.validated

import org.jetbrains.kotlin.GeneratedDeclarationKey

/** Names shared by the FIR part (declares validate()) and the IR part (writes it and the checks). */
object ValidatedNames {
    /** Package of the annotations in the runtime module. */
    const val PACKAGE = "io.github.abdullahkhan118.annotations"

    /** `@Validated`, put on a class to turn validation on. */
    const val ANNOTATION = "$PACKAGE.Validated"

    /** The public function that checks every annotated property: `user.validate()`. */
    const val VALIDATE = "validate"

    /** The private function checking only the properties declared in that class; called by its constructors. */
    const val VALIDATE_DECLARED = "validateDeclaredProperties"

    /** Name of the private check generated for one property, e.g. `age` -> `validateAge`. */
    fun propertyValidatorName(propertyName: String) =
        VALIDATE + propertyName.replaceFirstChar { it.uppercaseChar() }
}

/** Tag on the FIR-declared `validate()`, so the IR part can find it and write its body. */
object ValidatedKey : GeneratedDeclarationKey()

/** The type a constraint annotation applies to, as its fully qualified name. */
enum class ValueType(val fqName: String) {
    BYTE("kotlin.Byte"),
    SHORT("kotlin.Short"),
    INT("kotlin.Int"),
    LONG("kotlin.Long"),
    FLOAT("kotlin.Float"),
    DOUBLE("kotlin.Double"),
    STRING("kotlin.String"),
}

/** What a constraint annotation checks. */
enum class ConstraintKind { RANGE, DISTINCT, REGEX }

/**
 * Every constraint annotation the plugin understands, with what it applies to and checks.
 *
 * For ranges, [defaultMin]/[defaultMax] are used when the annotation leaves `min`/`max` out.
 * They must match the defaults declared in the annotations module's ValidationAnnotations.kt: the plugin can't
 * reliably read default values of an annotation compiled in another module, so it repeats them.
 */
enum class ConstraintAnnotation(
    val simpleName: String,
    val valueType: ValueType,
    val kind: ConstraintKind,
    val defaultMin: Any? = null,
    val defaultMax: Any? = null,
) {
    BYTE_RANGE("ByteRange", ValueType.BYTE, ConstraintKind.RANGE, Byte.MIN_VALUE, Byte.MAX_VALUE),
    SHORT_RANGE("ShortRange", ValueType.SHORT, ConstraintKind.RANGE, Short.MIN_VALUE, Short.MAX_VALUE),
    INT_RANGE("IntRange", ValueType.INT, ConstraintKind.RANGE, Int.MIN_VALUE, Int.MAX_VALUE),
    LONG_RANGE("LongRange", ValueType.LONG, ConstraintKind.RANGE, Long.MIN_VALUE, Long.MAX_VALUE),
    FLOAT_RANGE("FloatRange", ValueType.FLOAT, ConstraintKind.RANGE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY),
    DOUBLE_RANGE("DoubleRange", ValueType.DOUBLE, ConstraintKind.RANGE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY),

    BYTE_DISTINCT("ByteDistinct", ValueType.BYTE, ConstraintKind.DISTINCT),
    SHORT_DISTINCT("ShortDistinct", ValueType.SHORT, ConstraintKind.DISTINCT),
    INT_DISTINCT("IntDistinct", ValueType.INT, ConstraintKind.DISTINCT),
    LONG_DISTINCT("LongDistinct", ValueType.LONG, ConstraintKind.DISTINCT),
    FLOAT_DISTINCT("FloatDistinct", ValueType.FLOAT, ConstraintKind.DISTINCT),
    DOUBLE_DISTINCT("DoubleDistinct", ValueType.DOUBLE, ConstraintKind.DISTINCT),
    STRING_DISTINCT("StringDistinct", ValueType.STRING, ConstraintKind.DISTINCT),

    REGEX("Regex", ValueType.STRING, ConstraintKind.REGEX),
    ;

    /** e.g. "io.github.abdullahkhan118.annotations.IntRange". */
    val fqName: String get() = "${ValidatedNames.PACKAGE}.$simpleName"

    companion object {
        /** The constraint annotation with this fully qualified name, or null if it isn't one. */
        fun byFqName(fqName: String?): ConstraintAnnotation? = entries.firstOrNull { it.fqName == fqName }
    }
}
