package io.github.abdullahkhan118

import io.github.abdullahkhan118.annotations.ByteDistinct
import io.github.abdullahkhan118.annotations.ByteRange
import io.github.abdullahkhan118.annotations.Regex
import io.github.abdullahkhan118.annotations.StringDistinct
import io.github.abdullahkhan118.annotations.Validate

@Validate
class User(
    @ByteRange(10, 20)
    internal val age: Byte,
    @Regex("^[A-Za-z][A-Za-z '-]*$")
    val name: String,
    @ByteDistinct(1,2,3,4,5)
    val level: Byte,
    @StringDistinct("intern", "junior", "mid", "senior")
    val type: String
)