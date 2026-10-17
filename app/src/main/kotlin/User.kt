package io.github.abdullahkhan118

import io.github.abdullahkhan118.annotations.ByteDistinct
import io.github.abdullahkhan118.annotations.ByteRange
import io.github.abdullahkhan118.annotations.Regex
import io.github.abdullahkhan118.annotations.StringDistinct
import io.github.abdullahkhan118.annotations.Validated

/*
 * @Validated: the compiler plugin checks every annotated property when a User is created, and checks
 * `age` again whenever it's assigned. An invalid value throws IllegalArgumentException, so a User can
 * never hold invalid data. `user.validate()` re-checks everything on demand.
 */
@Validated
class User(
    @Regex("^[A-Za-z][A-Za-z '-]*$")
    val name: String,
    @ByteDistinct(1, 2, 3, 4, 5)
    val level: Byte,
    @StringDistinct("intern", "junior", "mid", "senior")
    val type: String
) {

    @ByteRange(10, 20)
    internal var age: Byte = 18

}
