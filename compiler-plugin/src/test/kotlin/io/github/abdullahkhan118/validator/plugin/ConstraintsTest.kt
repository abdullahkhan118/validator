package io.github.abdullahkhan118.validator.plugin

import kotlin.test.Test

/** Each kind of constraint, on single values. */
class ConstraintsTest {
    private val compiled = compileOk(
        """
        import io.github.abdullahkhan118.validator.annotations.*
        import io.github.abdullahkhan118.validator.annotations.IntRange
        import io.github.abdullahkhan118.validator.annotations.LongRange
        import io.github.abdullahkhan118.validator.annotations.Regex

        @Validated
        class Ranges(
            @ByteRange(1, 2) val byte: Byte = 1,
            @ShortRange(1, 2) val short: Short = 1,
            @IntRange(1, 2) val int: Int = 1,
            @LongRange(1, 2) val long: Long = 1,
            @FloatRange(0f, 1f) val float: Float = 0f,
            @DoubleRange(0.0, 1.0) val double: Double = 0.0,
            @IntRange(min = 0) val atLeast: Int = 0,
            @DoubleRange(max = 0.0) val atMost: Double = -1.0,
        )

        @Validated
        class Distinct(
            @IntDistinct(1, 3) val int: Int = 1,
            @DoubleDistinct(0.5) val double: Double = 0.5,
            @StringDistinct("a", "b") val string: String = "a",
        )

        @Validated
        class Patterns(
            @Regex("[a-z]+") val string: String = "abc",
            @Regex("[a-z]+") val builder: StringBuilder = StringBuilder("abc"),
            @Regex("[a-z]+") val sequence: CharSequence = "abc",
            @Regex("[a-z]+") val nullable: String? = null,
        )

        fun valid() { Ranges(); Ranges(byte = 2, int = 2, atLeast = Int.MAX_VALUE, atMost = Double.NEGATIVE_INFINITY); Distinct(int = 3, string = "b"); Patterns() }

        fun byteTooBig() { Ranges(byte = 3) }
        fun shortTooSmall() { Ranges(short = 0) }
        fun intTooBig() { Ranges(int = 3) }
        fun longTooSmall() { Ranges(long = 0) }
        fun floatTooBig() { Ranges(float = 1.5f) }
        fun doubleTooSmall() { Ranges(double = -0.5) }
        fun belowMin() { Ranges(atLeast = -1) }
        fun aboveMax() { Ranges(atMost = 0.1) }

        fun intNotListed() { Distinct(int = 2) }
        fun doubleNotListed() { Distinct(double = 0.25) }
        fun stringNotListed() { Distinct(string = "c") }

        fun partialMatch() { Patterns(string = "abc1") }
        fun builderMismatch() { Patterns(builder = StringBuilder("ABC")) }
        fun sequenceMismatch() { Patterns(sequence = StringBuilder("1")) }
        fun nullableMismatch() { Patterns(nullable = "X") }
        """,
    )

    @Test
    fun `valid values pass, including null and the edges of ranges`() {
        compiled.call("valid")
    }

    @Test
    fun `ranges reject values outside min and max`() {
        compiled.assertRejects("byteTooBig", "Ranges.byte must be between 1 and 2 but was 3")
        compiled.assertRejects("shortTooSmall", "Ranges.short must be between 1 and 2 but was 0")
        compiled.assertRejects("intTooBig", "Ranges.int must be between 1 and 2 but was 3")
        compiled.assertRejects("longTooSmall", "Ranges.long must be between 1 and 2 but was 0")
        compiled.assertRejects("floatTooBig", "Ranges.float must be between 0.0 and 1.0 but was 1.5")
        compiled.assertRejects("doubleTooSmall", "Ranges.double must be between 0.0 and 1.0 but was -0.5")
    }

    @Test
    fun `a range with one side left out has no limit on that side`() {
        compiled.assertRejects("belowMin", "Ranges.atLeast must be at least 0 but was -1")
        compiled.assertRejects("aboveMax", "Ranges.atMost must be at most 0.0 but was 0.1")
    }

    @Test
    fun `distinct rejects values not listed`() {
        compiled.assertRejects("intNotListed", "Distinct.int must be one of [1, 3] but was 2")
        compiled.assertRejects("doubleNotListed", "Distinct.double must be one of [0.5] but was 0.25")
        compiled.assertRejects("stringNotListed", "Distinct.string must be one of [\"a\", \"b\"] but was \"c\"")
    }

    @Test
    fun `regex must match the whole value, on any CharSequence`() {
        compiled.assertRejects("partialMatch", "Patterns.string must match the pattern [a-z]+ but was \"abc1\"")
        compiled.assertRejects("builderMismatch", "Patterns.builder must match the pattern [a-z]+ but was \"ABC\"")
        compiled.assertRejects("sequenceMismatch", "Patterns.sequence must match the pattern [a-z]+ but was \"1\"")
        compiled.assertRejects("nullableMismatch", "Patterns.nullable must match the pattern [a-z]+ but was \"X\"")
    }
}
