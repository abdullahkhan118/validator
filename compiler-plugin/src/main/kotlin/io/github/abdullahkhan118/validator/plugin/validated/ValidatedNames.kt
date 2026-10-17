package io.github.abdullahkhan118.validator.plugin.validated

import org.jetbrains.kotlin.GeneratedDeclarationKey

/** Names of @Validated and of the functions the plugin generates. */
object ValidatedNames {
    /** Package of the annotations in the runtime module. */
    const val PACKAGE = "io.github.abdullahkhan118.validator.annotations"

    /** `@Validated`, put on a class to turn validation on. */
    const val ANNOTATION = "$PACKAGE.Validated"

    /** The private function checking the properties declared in that class; called by its constructors. */
    const val VALIDATE = "validate"

    /** `@Validate`, put on a property holding @Validated objects to have them checked too. */
    const val VALIDATE_ANNOTATION = "$PACKAGE.Validate"

    /** The hidden public function other classes call to run a @Validated class's checks (see Bridges.kt). */
    const val BRIDGE = "validate\$validated"

    /** Name of the private check generated for one property, e.g. `age` -> `validateAge`. */
    fun propertyValidatorName(propertyName: String) =
        VALIDATE + propertyName.replaceFirstChar { it.uppercaseChar() }
}

/** Origin tag on every declaration the plugin generates. */
object ValidatedKey : GeneratedDeclarationKey()

/** The type of a constraint annotation's arguments (and of the values it checks), as its fully qualified name. */
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
 * [appliesTo] is the class a checked value must be (a subtype of): the property's type, or its element type for a
 * collection or array. Usually the [valueType] itself (only Int is an Int); `@Regex` takes any CharSequence.
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
    val appliesTo: String = valueType.fqName,
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

    REGEX("Regex", ValueType.STRING, ConstraintKind.REGEX, appliesTo = "kotlin.CharSequence"),
    ;

    /** e.g. "io.github.abdullahkhan118.validator.annotations.IntRange". */
    val fqName: String get() = "${ValidatedNames.PACKAGE}.$simpleName"

    companion object {
        /** The constraint annotation with this fully qualified name, or null if it isn't one. */
        fun byFqName(fqName: String?): ConstraintAnnotation? = entries.firstOrNull { it.fqName == fqName }
    }
}
