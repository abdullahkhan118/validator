package io.github.abdullahkhan118

fun main() {
    // Valid: created and updated without complaint.
    val user = User("abdullah", 2, "mid")
    user.age = 15
    user.tags = listOf("kotlin", "compiler")
    println("valid user: name=${user.name}, level=${user.level}, type=${user.type}, age=${user.age}, tags=${user.tags}")

    // Invalid: rejected at creation...
    rejected("a name starting with a digit") { User("1abdullah", 2, "mid") }
    rejected("level 9") { User("abdullah", 9, "mid") }
    rejected("type \"boss\"") { User("abdullah", 2, "boss") }
    // ...and at update, leaving the old value in place.
    rejected("age 25") { user.age = 25 }
    println("age is still ${user.age}")
    rejected("tag \"Kotlin\"") { user.tags = listOf("compiler", "Kotlin") }
}

/** Runs [block], which should throw, and prints why it was rejected. */
fun rejected(what: String, block: () -> Unit) {
    try {
        block()
        println("NOT rejected: $what")
    } catch (e: IllegalArgumentException) {
        println("rejected $what: ${e.message}")
    }
}
