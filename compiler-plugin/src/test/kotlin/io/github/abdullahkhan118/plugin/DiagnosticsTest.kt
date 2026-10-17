package io.github.abdullahkhan118.plugin

import org.jetbrains.kotlin.cli.common.ExitCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Misused annotations are compile errors (or warnings) pointing at the source. */
class DiagnosticsTest {
    private val imports = """
        import io.github.abdullahkhan118.annotations.*
        import io.github.abdullahkhan118.annotations.IntRange
        import io.github.abdullahkhan118.annotations.Regex
    """.trimIndent()

    /** Compiles [declarations] and asserts it fails with exactly [expected] errors (each matched by its end). */
    private fun assertErrors(declarations: String, vararg expected: String) {
        val result = compile(imports + "\n" + declarations.trimIndent())
        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertEquals(expected.size, result.errors.size, result.errors.joinToString("\n"))
        expected.forEach { message ->
            assertTrue(result.errors.any { it.endsWith(message) }, "missing: $message\nin:\n" + result.errors.joinToString("\n"))
        }
    }

    @Test
    fun `annotation on the wrong type`() = assertErrors(
        """
        @Validated
        class Bad(
            @ByteRange(1, 2) val int: Int,
            @IntRange(1, 2) val longs: List<Long>,
            @IntRange(1, 2) val text: String,
            @StringDistinct("a") val sequence: CharSequence,
        )
        """,
        "@ByteRange can only be used on a kotlin.Byte property, or a collection or array of them, but Bad.int is kotlin.Int",
        "@IntRange can only be used on a kotlin.Int property, or a collection or array of them, but Bad.longs is kotlin.collections.List<kotlin.Long>",
        "@IntRange can only be used on a kotlin.Int property, or a collection or array of them, but Bad.text is kotlin.String",
        "@StringDistinct can only be used on a kotlin.String property, or a collection or array of them, but Bad.sequence is kotlin.CharSequence",
    )

    @Test
    fun `unsupported collection shapes`() = assertErrors(
        """
        @Validated
        class Bad(
            @IntRange(1, 2) val nested: List<List<Int>>,
            @IntRange(1, 2) val map: Map<String, Int>,
            @IntRange(1, 2) val star: List<*>,
        )
        """,
        "but Bad.nested is kotlin.collections.List<kotlin.collections.List<kotlin.Int>>",
        "but Bad.map is kotlin.collections.Map<kotlin.String, kotlin.Int>",
        "but Bad.star is kotlin.collections.List<*>",
    )

    @Test
    fun `invalid annotation arguments`() = assertErrors(
        """
        @Validated
        class Bad(
            @IntRange(10, 1) val minAboveMax: Int,
            @IntDistinct val noValues: Int,
            @Regex("[") val brokenPattern: String,
            @Regex("") val emptyPattern: List<String>,
        )
        """,
        "@IntRange on Bad.minAboveMax has min (10) greater than max (1)",
        "@IntDistinct on Bad.noValues lists no allowed values",
        "@Regex on Bad.brokenPattern has an invalid pattern: Unclosed character class near index 0",
        "@Regex on Bad.emptyPattern has an empty pattern",
    )

    @Test
    fun `errors point at the annotation`() {
        val result = compile(imports + "\n@Validated class Bad(@ByteRange(1, 2) val int: Int)")
        assertTrue(result.errors.single().startsWith("4:22 "), result.errors.single())
    }

    @Test
    fun `validated on something other than a class`() = assertErrors(
        """
        @Validated interface Named
        @JvmInline @Validated value class Id(val id: Int)
        """,
        "@Validated can only be used on classes and objects, not on interfaces or annotation classes",
        "@Validated can only be used on classes and objects, not on value classes",
    )

    @Test
    fun `a validate() of the class's own clashes with the generated one`() = assertErrors(
        """
        @Validated class Declares(@IntRange(0, 1) val bit: Int) { fun validate() {} }
        open class HasValidate { open fun validate() {} }
        @Validated class Inherits(@IntRange(0, 1) val bit: Int) : HasValidate()
        """,
        "@Validated generates a private 'validate()', so Declares can't declare one. Rename it or remove the @Validated annotation.",
        "@Validated generates a private 'validate()', so Inherits can't inherit one. Rename it or remove the @Validated annotation.",
    )

    @Test
    fun `constraints without @Validated are a warning`() {
        val result = compile(imports + "\nclass Plain(@IntRange(0, 1) val bit: Int)")
        assertEquals(ExitCode.OK, result.exitCode)
        assertTrue(
            result.warnings.any { it.endsWith("Plain.bit has constraints, but they have no effect: Plain isn't annotated with @Validated") },
            result.warnings.joinToString("\n"),
        )
    }
}
