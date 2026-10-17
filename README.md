# validator

A Kotlin compiler plugin that builds validation into your classes, driven by
constraint annotations (`@ByteRange`, `@Regex`, `@IntDistinct`, ...).

Unlike a separate validator you have to remember to call, the checks run
automatically: in every constructor, and in the setter of every annotated
`var`. Misused annotations (wrong type, `min > max`, an invalid regex) are
compile errors that point at the source line.

## Compiler plugin: `@Validated`

Annotate a class with `@Validated` and its properties with constraints. The
plugin builds the checks into the class, so **an invalid object can't be
created, and an existing one can't be made invalid**: a failed check throws
`IllegalArgumentException`, and a rejected assignment leaves the old value in
place.

```kotlin
// app/src/main/kotlin/User.kt
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
```

```kotlin
val user = User("abdullah", 2, "mid")   // OK
user.age = 15                           // OK
user.validate()                         // generated: re-checks every property on demand

User("abdullah", 9, "mid")  // IllegalArgumentException: User.level must be one of [1, 2, 3, 4, 5] but was 9
user.age = 25               // IllegalArgumentException: User.age must be between 10 and 20 but was 25
                            // (user.age is still 15)
```

### What the plugin generates

The class behaves as if it had been written like this:

```kotlin
class User(name: String, level: Byte, type: String) {
    internal var age: Byte = 18
        set(value) { validateAge(value); field = value }    // setters check first

    fun validate() = validateDeclaredProperties()           // public, for on-demand checks

    private fun validateDeclaredProperties() {               // this class's own properties
        validateName(name); validateLevel(level); validateType(type); validateAge(age)
    }

    private fun validateAge(value: Byte) {                   // one check per property
        if (value < 10 || value > 20) throw IllegalArgumentException("User.age must be between 10 and 20 but was $value")
    }
    // ...
}
// ...and every constructor ends with validateDeclaredProperties().
```

It works with:

- constructor `val`/`var`s and properties declared in the class body,
- nullable properties (`null` always passes; use a non-null type to forbid it),
- `lateinit var` (checked once it's assigned),
- `private var` (checked when changed from inside the class),
- secondary constructors, data class `copy()`, and `object`s,
- inheritance: each class validates its own properties during construction,
  and `validate()` on a subclass checks the whole hierarchy.

### Compile-time errors

Misused annotations fail the build, pointing at the annotation:

```
e: Bad.kt:8:11 [validated] @ByteRange can only be used on a kotlin.Byte property, but Bad.wrongType is kotlin.Int
e: Bad.kt:8:48 [validated] @IntRange on Bad.minAboveMax has min (10) greater than max (1)
e: Bad.kt:8:99 [validated] @IntDistinct on Bad.noValues lists no allowed values
e: Bad.kt:8:133 [validated] @Regex on Bad.brokenPattern has an invalid pattern: Unclosed character class near index 3
```

Also an error: `@Validated` on an interface, annotation class or value class,
and a `@Validated` class that declares its own `validate()`. Constraints in a
class without `@Validated` are a warning (they'd have no effect).

### Using it

The plugin is loaded straight from its jar (see `app/build.gradle.kts`):

```kotlin
evaluationDependsOn(":compiler-plugin")
val validatorPluginJar = project(":compiler-plugin").tasks.named<Jar>("jar")

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            validatorPluginJar.flatMap { it.archiveFile }.map { listOf("-Xplugin=${it.asFile.absolutePath}") },
        )
    }
}

tasks.named("compileKotlin") {
    dependsOn(validatorPluginJar)
    inputs.files(validatorPluginJar).withPropertyName("validatorPluginJar")
}
```

### How it's built

| Package (`compiler-plugin`) | Role |
| --- | --- |
| `plugin` | `ValidatorCompilerPluginRegistrar`, the entry point (found via `META-INF/services`) |
| `plugin.validated` | names, and the table of constraint annotations |
| `plugin.validated.fir` | FIR: declares the public `validate()` so user code can call it |
| `plugin.validated.ir` | IR: reads the annotations, reports misuse, builds the checks, wires them into constructors and setters |
| `plugin.util.fir` / `plugin.util.ir` | reusable helpers (`BaseFirDeclarationGenerationExtension`, `addCallAtStart`, ...) |

## Supported annotations

All in the `annotations` module (`io.github.abdullahkhan118.annotations`).
They can only be placed on properties (including `val`/`var` constructor parameters).

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
| `@Regex(pattern)` | `String` | the whole value matches `pattern` |

For ranges, leaving `min` or `max` out means no limit on that side.

`IntRange`, `LongRange` and `Regex` share their names with Kotlin's own
`kotlin.ranges.IntRange`, `kotlin.ranges.LongRange` and `kotlin.text.Regex`,
which are imported by default. Import them explicitly
(`import io.github.abdullahkhan118.annotations.IntRange`); a star import
loses to Kotlin's default imports.

## Project layout

Multi-module Gradle project, versions pinned in `gradle/libs.versions.toml`:

| Module | Purpose |
| --- | --- |
| `annotations` | The annotations (`@Validated`, `@ByteRange`, `@Regex`, ...). What consumer code depends on. |
| `compiler-plugin` | The Kotlin compiler plugin behind `@Validated`. |
| `app` | Example consumer using the compiler plugin (`User.kt`, `Main.kt`). |

## Building and running

```bash
./gradlew build
./gradlew :app:run
```

## Requirements

- JDK 17 (configured via `jvmToolchain(17)` in each module).
- The compiler plugin uses the Kotlin compiler's internal APIs, so it must be
  built with exactly the same Kotlin version as the code it compiles
  (`kotlin` in `gradle/libs.versions.toml`, currently 2.4.10).

## Known limitations

- A `@Validated` subclass is linked to a `@Validated` superclass only when
  both are compiled in the same module.
- Code that creates objects without calling a constructor (reflection, some
  deserializers) or writes fields directly isn't checked until `validate()`
  is called.
- IntelliJ only runs its bundled compiler plugins by default, so it shows
  `user.validate()` as unresolved even though the build works. To fix it,
  turn off the registry key `kotlin.k2.only.bundled.compiler.plugins.enabled`
  and reload the Gradle project.
- `@Regex` builds a new `Regex` for each check.

## Troubleshooting

If a build fails with `Operation timed out` while reading a jar under
`build/`, macOS iCloud ("Optimize Mac Storage") may have offloaded that file
(`ls -lO` shows `dataless`). Delete the `build/` directories and build again.
