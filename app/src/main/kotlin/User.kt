package io.github.abdullahkhan118

import io.github.abdullahkhan118.annotations.ByteRange
import io.github.abdullahkhan118.annotations.Validate

@Validate
class User(
    @ByteRange(10, 20)
    val age: Byte
) {
    private lateinit var name: String

    fun printAge() {
        println(age)
    }

}