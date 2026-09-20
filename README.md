# validator

A Kotlin Symbol Processing (KSP) based compile-time validator generator.

Annotate a class's constructor properties with constraint annotations
(`@ByteRange`, `@Regex`, `@IntDistinct`, ...) and the processor generates a
companion `*Validator` class that checks every annotated property and throws
an `IllegalArgumentException` on the first constraint that fails.

## How it works

1. Annotate a class with `@Validate`.
2. Annotate its constructor properties with one or more constraint
   annotations from the `annotations` module.
3. KSP (via the `processor` module) generates a `<ClassName>Validator` class
   next to it, wrapping an instance of the annotated class and exposing a
   single `validate()` function.
4. Call `validate()` after constructing the object to enforce the
   constraints.

### Example

```kotlin
// app/src/main/kotlin/User.kt
@Validate
class User(
    @ByteRange(10, 20)
    val age: Byte,
    @Regex("^[A-Za-z][A-Za-z '-]*$")
    val name: String,
    @ByteDistinct(1, 2, 3, 4, 5)
    val level: Byte,
    @StringDistinct("intern", "junior", "mid", "senior")
    val type: String
)
```

```kotlin
// app/src/main/kotlin/Main.kt
fun main() {
    UserValidator(User(age = 15, name = "abdullah", level = 2, type = "mid")).validate()
}
```

Generated output (`build/generated/ksp/main/kotlin/.../UserGenerated.kt`):

```kotlin
class UserValidator(
    private val user: io.github.abdullahkhan118.User
) {
    fun validate() {
        ageIsValid(user.age)
        nameIsValid(user.name)
        levelIsValid(user.level)
        typeIsValid(user.type)
    }

    private fun ageIsValid(age: kotlin.Byte) =
        if (age < 10 || age > 20) throw IllegalArgumentException("...age is out of range") else true

    private fun nameIsValid(name: kotlin.String) =
        if (!Regex("^[A-Za-z][A-Za-z '-]*$").matches(name)) throw IllegalArgumentException("...name does not match the pattern ...") else true

    // ...
}
```

`validate()` throws on the first failing property; if it returns normally,
every annotated property satisfied its constraint.

## Supported annotations

All defined in the `annotations` module
(`io.github.abdullahkhan118.annotations`):

| Annotation | Applies to | Checks |
| --- | --- | --- |
| `@ByteRange(min, max)` | `Byte` | value is within `[min, max]` |
| `@ShortRange(min, max)` | `Short` | value is within `[min, max]` |
| `@IntRange(min, max)` | `Int` | value is within `[min, max]` |
| `@LongRange(min, max)` | `Long` | value is within `[min, max]` |
| `@FloatRange(min, max)` | `Float` | value is within `[min, max]` |
| `@DoubleRange(min, max)` | `Double` | value is within `[min, max]` |
| `@ByteDistinct(vararg values)` | `Byte` | value is one of `values` |
| `@ShortDistinct(vararg values)` | `Short` | value is one of `values` |
| `@IntDistinct(vararg values)` | `Int` | value is one of `values` |
| `@LongDistinct(vararg values)` | `Long` | value is one of `values` |
| `@FloatDistinct(vararg values)` | `Float` | value is one of `values` |
| `@DoubleDistinct(vararg values)` | `Double` | value is one of `values` |
| `@StringDistinct(vararg values)` | `String` | value is one of `values` |
| `@Regex(pattern)` | `String` | value matches `pattern` |

A class must be annotated with `@Validate` for the processor to generate a
validator for it.

## Project layout

This is a multi-module Gradle project, versions pinned via the version
catalog at `gradle/libs.versions.toml`:

| Module | Purpose |
| --- | --- |
| `annotations` | The constraint annotations (`@Validate`, `@ByteRange`, `@Regex`, ...). No KSP dependency — this is what consumer code depends on at compile time. |
| `processor` | The KSP `SymbolProcessorProvider`/`SymbolProcessor` that walks `@Validate`-annotated classes and generates the `*Validator` classes. |
| `app` | Example consumer module: applies the `ksp` plugin, depends on `annotations` + `processor`, and demonstrates usage (`User.kt`, `Main.kt`). |

## Building

```bash
./gradlew build
```

The `app` module doesn't apply the `application` plugin, so there's no
`run` task; run `Main.kt` from your IDE, or add
`plugins { application }` + `application { mainClass = "..." }` if you want
a `./gradlew :app:run` task.

Inspect what KSP generated for a given module:

```bash
./gradlew :app:kspKotlin
find app/build/generated/ksp -name '*.kt'
```

## Requirements

- JDK 17 (configured via `jvmToolchain(17)` in each module).
- The Kotlin and KSP plugin versions must stay in lockstep — KSP releases
  are versioned `<kotlinVersion>-<kspBuild>` and only work against that
  exact Kotlin compiler version. See `gradle/libs.versions.toml`.

## Known limitations

- The annotated class must be visible to its generated `*Validator` (avoid
  `private` top-level classes if you construct the validator from another
  file).
- Only primary-constructor properties are validated; properties declared in
  the class body (e.g. `lateinit var`) are not constraint-checked, though
  they no longer break generation.
- `validate()` throws on the first invalid property rather than collecting
  every violation.
