package io.github.abdullahkhan118.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

/** Constraints on collections and arrays check every element. */
class CollectionsTest {
    private val compiled = compileOk(
        """
        import io.github.abdullahkhan118.annotations.*
        import io.github.abdullahkhan118.annotations.IntRange
        import io.github.abdullahkhan118.annotations.LongRange
        import io.github.abdullahkhan118.annotations.Regex

        class Scores(vararg scores: Int) : ArrayList<Int>(scores.toList())
        abstract class Box<T> : Iterable<T>
        class Words(private val words: List<String>) : Box<String>() { override fun iterator() = words.iterator() }

        @Validated
        class Holder<T : List<Int>>(
            @IntRange(1, 5) val list: List<Int> = listOf(1, 5),
            @IntRange(1, 5) val set: Set<Int>? = setOf(3),
            @IntRange(1, 5) val nullableElements: List<Int?> = listOf(null, 2),
            @IntRange(1, 5) val custom: Scores = Scores(4),
            @IntRange(1, 5) val generic: T,
            @Regex("[a-z]+") val words: Words = Words(listOf("a")),
            @IntDistinct(1, 2) val ints: IntArray = intArrayOf(1, 2),
            @LongRange(max = 0) val longs: LongArray = longArrayOf(-1),
            @FloatDistinct(0.5f) val floats: FloatArray = floatArrayOf(0.5f),
            @StringDistinct("a", "b") val strings: Array<String> = arrayOf("a"),
            @Regex("[a-z]+") val builders: List<StringBuilder> = listOf(StringBuilder("ab")),
        ) {
            @IntRange(1, 5) var mutable: List<Int> = listOf(1)
        }

        fun holder() = Holder(generic = listOf(1))

        fun valid() { holder(); Holder(set = null, generic = emptyList()); Holder(list = emptyList(), generic = listOf(5)) }
        fun list() { Holder(list = listOf(1, 9), generic = listOf(1)) }
        fun set() { Holder(set = linkedSetOf(1, 0), generic = listOf(1)) }
        fun nullableElements() { Holder(nullableElements = listOf(null, 7), generic = listOf(1)) }
        fun custom() { Holder(custom = Scores(1, 2, 6), generic = listOf(1)) }
        fun generic() { Holder(generic = listOf(8)) }
        fun words() { Holder(words = Words(listOf("ok", "Nope")), generic = listOf(1)) }
        fun ints() { Holder(ints = intArrayOf(3), generic = listOf(1)) }
        fun longs() { Holder(longs = longArrayOf(0, 1), generic = listOf(1)) }
        fun floats() { Holder(floats = floatArrayOf(0.25f), generic = listOf(1)) }
        fun strings() { Holder(strings = arrayOf("a", "c"), generic = listOf(1)) }
        fun builders() { Holder(builders = listOf(StringBuilder("A")), generic = listOf(1)) }

        val shared = holder()
        fun setInvalid() { shared.mutable = listOf(1, 2, 0) }
        fun mutableAfterRejection() = shared.mutable
        """,
    )

    @Test
    fun `valid collections pass, including empty and null ones`() {
        compiled.call("valid")
    }

    @Test
    fun `lists, sets and arrays report the index of the bad element`() {
        compiled.assertRejects("list", "Holder.list[1] must be between 1 and 5 but was 9")
        compiled.assertRejects("set", "Holder.set[1] must be between 1 and 5 but was 0")
        compiled.assertRejects("ints", "Holder.ints[0] must be one of [1, 2] but was 3")
        compiled.assertRejects("longs", "Holder.longs[1] must be at most 0 but was 1")
        compiled.assertRejects("floats", "Holder.floats[0] must be one of [0.5] but was 0.25")
        compiled.assertRejects("strings", "Holder.strings[1] must be one of [\"a\", \"b\"] but was \"c\"")
    }

    @Test
    fun `null elements pass, others are checked`() {
        compiled.assertRejects("nullableElements", "Holder.nullableElements[1] must be between 1 and 5 but was 7")
    }

    @Test
    fun `element types are found through subclasses and type parameters`() {
        compiled.assertRejects("custom", "Holder.custom[2] must be between 1 and 5 but was 6")
        compiled.assertRejects("generic", "Holder.generic[0] must be between 1 and 5 but was 8")
        compiled.assertRejects("words", "Holder.words[1] must match the pattern [a-z]+ but was \"Nope\"")
        compiled.assertRejects("builders", "Holder.builders[0] must match the pattern [a-z]+ but was \"A\"")
    }

    @Test
    fun `assigning an invalid collection is rejected and keeps the old one`() {
        compiled.assertRejects("setInvalid", "Holder.mutable[2] must be between 1 and 5 but was 0")
        assertEquals(listOf(1), compiled.call("mutableAfterRejection"))
    }
}
