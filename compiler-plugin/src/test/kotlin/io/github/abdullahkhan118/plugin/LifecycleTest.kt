package io.github.abdullahkhan118.plugin

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** When the checks run: constructors, setters, objects, inheritance; and that validate() stays private. */
class LifecycleTest {
    private val compiled = compileOk(
        """
        import io.github.abdullahkhan118.annotations.*
        import io.github.abdullahkhan118.annotations.IntRange
        import io.github.abdullahkhan118.annotations.Regex

        @Validated
        class Account(@IntRange(0, 9) var balance: Int) {
            @IntRange(0, 1) private var flag: Int = 0
            @Regex("[a-z]+") lateinit var nick: String
            @IntRange(0, 9) val derived: Int
            init { derived = balance * 2 }
            constructor(text: String) : this(0) { balance = text.length }
            fun changeFlag(value: Int) { flag = value }
        }

        @Validated data class Point(@IntRange(0, 9) val x: Int)

        @Validated object GoodConfig { @IntRange(1, 3) val level = 2 }
        @Validated object BadConfig { @IntRange(1, 3) val level = 5 }

        @Validated open class Animal(@IntRange(1, 4) val legs: Int)
        @Validated class Spider(@IntRange(8, 8) val eyes: Int, legs: Int = 4) : Animal(legs)
        open class Plain
        @Validated class OnPlain(@IntRange(0, 1) val bit: Int) : Plain()

        val account = Account(4)
        fun valid() { Account("abc"); Account(4); Point(1).copy(x = 2); GoodConfig.level; Spider(8); OnPlain(1) }

        fun constructorArgument() { Account(10) }
        fun initBlock() { Account(5) }
        fun secondaryConstructor() { Account("a long text") }
        fun setter() { account.balance = 42 }
        fun balanceAfterRejection() = account.balance
        fun privateSetter() { account.changeFlag(2) }
        fun lateinitUnsetPasses() = Account(1).toString()
        fun lateinitSetter() { account.nick = "Nope" }
        fun dataCopy() { Point(1).copy(x = 10) }
        fun badObject() = BadConfig.level
        fun subclassProperty() { Spider(7) }
        fun superclassProperty() { Spider(8, legs = 9) }
        fun nonValidatedSuperclass() { OnPlain(2) }
        """,
    )

    @Test
    fun `valid objects are created`() {
        compiled.call("valid")
        compiled.call("lateinitUnsetPasses")
    }

    @Test
    fun `every constructor validates, after init blocks`() {
        compiled.assertRejects("constructorArgument", "Account.balance must be between 0 and 9 but was 10")
        compiled.assertRejects("initBlock", "Account.derived must be between 0 and 9 but was 10")
        compiled.assertRejects("secondaryConstructor", "Account.balance must be between 0 and 9 but was 11")
        compiled.assertRejects("dataCopy", "Point.x must be between 0 and 9 but was 10")
    }

    @Test
    fun `setters reject invalid values and keep the old one`() {
        compiled.assertRejects("setter", "Account.balance must be between 0 and 9 but was 42")
        assertEquals(4, compiled.call("balanceAfterRejection"))
        compiled.assertRejects("privateSetter", "Account.flag must be between 0 and 1 but was 2")
        compiled.assertRejects("lateinitSetter", "Account.nick must match the pattern [a-z]+ but was \"Nope\"")
    }

    @Test
    fun `an object is validated when it's initialized`() {
        val e = assertFailsWith<ExceptionInInitializerError> { compiled.call("badObject") }
        assertEquals("BadConfig.level must be between 1 and 3 but was 5", e.cause?.message)
    }

    @Test
    fun `each class in a hierarchy validates its own properties`() {
        compiled.assertRejects("subclassProperty", "Spider.eyes must be between 8 and 8 but was 7")
        compiled.assertRejects("superclassProperty", "Animal.legs must be between 1 and 4 but was 9")
        compiled.assertRejects("nonValidatedSuperclass", "OnPlain.bit must be between 0 and 1 but was 2")
    }

    @Test
    fun `validate() is private in every validated class`() {
        listOf("Account", "Point", "GoodConfig", "Animal", "Spider", "OnPlain").forEach { name ->
            val validate = compiled.classLoader.loadClass(name).getDeclaredMethod("validate")
            assertTrue(Modifier.isPrivate(validate.modifiers), "$name.validate() should be private")
        }
    }

    @Test
    fun `validate() can't be called from outside the class`() {
        val result = compile(
            """
            import io.github.abdullahkhan118.annotations.*
            @Validated class A(@IntDistinct(1) val one: Int)
            fun f() = A(1).validate()
            """,
        )
        assertTrue(result.errors.any { "validate" in it }, result.errors.toString())
    }
}
