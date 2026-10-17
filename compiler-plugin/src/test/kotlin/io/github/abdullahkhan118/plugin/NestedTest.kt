package io.github.abdullahkhan118.plugin

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** `@Validate`: properties holding @Validated objects run those objects' checks too. */
class NestedTest {
    // A nested @Validated object can't be made invalid through its setters (they check), but it can by changing
    // a collection, array or StringBuilder it holds in place: that's what @Validate catches.
    private val compiled = compileOk(
        """
        import io.github.abdullahkhan118.annotations.*
        import io.github.abdullahkhan118.annotations.IntRange
        import io.github.abdullahkhan118.annotations.Regex

        // Declared after its users: bridges exist before any class is transformed.
        @Validated
        class User(
            @Validate val address: Address,
            @Validate val previous: Address? = null,
            @Validate val others: List<Address> = emptyList(),
            @Validate val array: Array<Address?> = emptyArray(),
            @Validate val pet: Animal = Animal(intArrayOf(4)),
        ) {
            @Validate var current: Address = address
        }

        @Validated
        class Address(@Regex("[0-9]{5}") val zip: StringBuilder, @Validate val geo: Geo = Geo(intArrayOf(0)))

        @Validated class Geo(@IntRange(-90, 90) val lat: IntArray)

        @Validated open class Animal(@IntRange(1, 4) val legs: IntArray)
        class Dog(legs: Int) : Animal(intArrayOf(legs))
        @Validated class Spider(@IntRange(8, 8) val eyes: IntArray) : Animal(intArrayOf(4))

        val good = Address(StringBuilder("12345"))
        fun bad() = Address(StringBuilder("12345")).also { it.zip.setLength(1) }

        fun valid() { User(good, previous = null, others = listOf(good), array = arrayOf(null, good), pet = Spider(intArrayOf(8))); User(good, pet = Dog(2)) }
        fun single() { User(bad()) }
        fun nullable() { User(good, previous = bad()) }
        fun list() { User(good, others = listOf(good, bad())) }
        fun array() { User(good, array = arrayOf(null, bad())) }
        fun deep() { User(Address(StringBuilder("12345")).also { it.geo.lat[0] = 100 }) }
        fun subclassWithoutAnnotation() { User(good, pet = Dog(1).also { it.legs[0] = 9 }) }
        fun subclassChecksSuperclass() { User(good, pet = Spider(intArrayOf(8)).also { it.legs[0] = 0 }) }
        fun subclassChecksItself() { User(good, pet = Spider(intArrayOf(8)).also { it.eyes[0] = 2 }) }

        val user = User(good)
        fun setter() { user.current = bad() }
        fun currentAfterRejection() = user.current === good
        """,
    )

    @Test
    fun `valid nested objects pass, and null passes`() {
        compiled.call("valid")
    }

    @Test
    fun `an invalid nested object is rejected, naming the property`() {
        compiled.assertRejects("single", "User.address is invalid: Address.zip must match the pattern [0-9]{5} but was \"1\"")
        compiled.assertRejects("nullable", "User.previous is invalid: Address.zip must match the pattern [0-9]{5} but was \"1\"")
    }

    @Test
    fun `nested objects in collections and arrays are checked one by one`() {
        compiled.assertRejects("list", "User.others[1] is invalid: Address.zip must match the pattern [0-9]{5} but was \"1\"")
        compiled.assertRejects("array", "User.array[1] is invalid: Address.zip must match the pattern [0-9]{5} but was \"1\"")
    }

    @Test
    fun `nesting goes any number of levels deep`() {
        compiled.assertRejects("deep", "User.address is invalid: Address.geo is invalid: Geo.lat[0] must be between -90 and 90 but was 100")
    }

    @Test
    fun `the object is checked as what it really is, with all its validated classes`() {
        compiled.assertRejects("subclassWithoutAnnotation", "User.pet is invalid: Animal.legs[0] must be between 1 and 4 but was 9")
        compiled.assertRejects("subclassChecksSuperclass", "User.pet is invalid: Animal.legs[0] must be between 1 and 4 but was 0")
        compiled.assertRejects("subclassChecksItself", "User.pet is invalid: Spider.eyes[0] must be between 8 and 8 but was 2")
    }

    @Test
    fun `assigning an invalid nested object is rejected and keeps the old one`() {
        compiled.assertRejects("setter", "User.current is invalid: Address.zip must match the pattern [0-9]{5} but was \"1\"")
        assertEquals(true, compiled.call("currentAfterRejection"))
    }

    @Test
    fun `the original exception is kept as the cause`() {
        val e = assertFailsWith<IllegalArgumentException> { compiled.call("single") }
        assertIs<IllegalArgumentException>(e.cause)
        assertEquals("Address.zip must match the pattern [0-9]{5} but was \"1\"", e.cause?.message)
    }

    @Test
    fun `the bridge is public but synthetic, and validate() stays private`() {
        val address = compiled.classLoader.loadClass("Address")
        val bridge = address.getDeclaredMethod("validate\$validated")
        assertTrue(Modifier.isPublic(bridge.modifiers) && bridge.isSynthetic, "bridge: ${Modifier.toString(bridge.modifiers)}")
        assertTrue(Modifier.isPrivate(address.getDeclaredMethod("validate").modifiers))
    }

    @Test
    fun `the bridge can't be called from Kotlin source`() {
        val result = compile(
            """
            import io.github.abdullahkhan118.annotations.*
            @Validated class A(@IntDistinct(1) val one: Int)
            fun f() = A(1).`validate${'$'}validated`()
            """,
        )
        assertTrue(result.errors.any { "validate\$validated" in it }, result.errors.toString())
    }

    @Test
    fun `nested classes from another module are checked`() {
        val library = compileOk(
            """
            package lib
            import io.github.abdullahkhan118.annotations.*
            import io.github.abdullahkhan118.annotations.IntRange
            @Validated open class Base(@IntRange(0, 9) val level: IntArray)
            @Validated class Item(@IntRange(1, 5) val size: IntArray) : Base(intArrayOf(0))
            """,
        )
        val app = compileOk(
            """
            import io.github.abdullahkhan118.annotations.*
            import lib.*
            @Validated class Order(@Validate val items: List<Item>)
            // Extends a @Validated class of the library: its bridge overrides the library's.
            @Validated class Extra(@Validate val base: Base) : Base(intArrayOf(1))
            fun item(size: Int) = Item(intArrayOf(size))
            fun valid() { Order(listOf(item(1))); Extra(item(5)) }
            fun bad() { Order(listOf(item(1), item(1).also { it.size[0] = 7 })) }
            fun badSuper() { Extra(item(1).also { it.level[0] = 10 }) }
            """,
            dependencies = listOf(library),
        )
        app.call("valid")
        app.assertRejects("bad", "Order.items[1] is invalid: Item.size[0] must be between 1 and 5 but was 7")
        app.assertRejects("badSuper", "Extra.base is invalid: Base.level[0] must be between 0 and 9 but was 10")
    }
}
