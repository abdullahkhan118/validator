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

    private fun validate() {                                 // this class's own properties
        validateName(name); validateLevel(level); validateType(type); validateAge(age)
    }

    private fun validateAge(value: Byte) {                   // one check per property
        if (value < 10 || value > 20) throw IllegalArgumentException("User.age must be between 10 and 20 but was $value")
    }
    // ...
}
// ...and every constructor ends with validate().
```

It works with:

- constructor `val`/`var`s and properties declared in the class body,
- nullable properties (`null` always passes; use a non-null type to forbid it),
- `lateinit var` (checked once it's assigned),
- `private var` (checked when changed from inside the class),
- secondary constructors, data class `copy()`, and `object`s,
- inheritance: each `@Validated` class in a hierarchy validates its own
  properties at the end of its own constructor,
- subtypes: a constraint fits any subtype of the type it applies to, e.g.
  `@Regex` on a `String`, `StringBuilder` or any other `CharSequence`,
- collections and arrays: on a `List<Int>`, `Set<Int>`, `IntArray`,
  `Array<Int>`, or any other `Iterable` subtype (`ArrayList<Int>`, your own
  `class Scores : ArrayList<Int>()`, a `T : List<Int>`), the constraint checks
  every element. A `null` element passes.

```kotlin
@Validated
class Team(@IntRange(1, 5) val ratings: List<Int>, @Regex("[a-z]+") val tags: Set<String>)

Team(listOf(4, 9), setOf("kotlin"))  // IllegalArgumentException: Team.ratings[1] must be between 1 and 5 but was 9
```

For a `Set` or other unordered collection, the `[index]` is the position in
iteration order.

### Nested objects: `@Validate`

A property annotated `@Validate` holds `@Validated` objects, or a collection
or array of them. Their checks run whenever the owner checks the property:
when the owner is created, and when the property is assigned.

```kotlin
@Validated class Address(@Regex("[0-9]{5}") val zip: StringBuilder)

@Validated
class User(@Validate val home: Address, @Validate val others: List<Address> = emptyList())

val address = Address(StringBuilder("12345"))
address.zip.setLength(1)    // changed in place: no setter, so nothing checks it here
User(address)               // IllegalArgumentException: User.home is invalid: Address.zip must match the pattern [0-9]{5} but was "1"
```

A `@Validated` object can't be made invalid through its setters, so this
catches changes made in place (collections, arrays, `StringBuilder`s) and
objects created without a constructor. Nesting goes any number of levels deep,
the nested object's exception is kept as the `cause`, and `null` passes.

The object is checked as what it really is: a property typed `Animal` holding
a `@Validated class Spider : Animal` runs both classes' checks. The type may
also be a non-`@Validated` subclass of a `@Validated` class.

`validate()` stays private. To reach it, each `@Validated` class also gets a
`validate$validated()` bridge. The bridge is public in the bytecode so that
other classes and modules can call it, but it's hidden from source code: it's
left out of the Kotlin metadata and marked synthetic for Java.

### Compile-time errors

Misused annotations fail the build, pointing at the annotation:

```
e: Bad.kt:8:11 [validated] @ByteRange can only be used on a kotlin.Byte property, or a collection or array of them, but Bad.wrongType is kotlin.Int
e: Bad.kt:8:48 [validated] @IntRange on Bad.minAboveMax has min (10) greater than max (1)
e: Bad.kt:8:99 [validated] @IntDistinct on Bad.noValues lists no allowed values
e: Bad.kt:8:133 [validated] @Regex on Bad.brokenPattern has an invalid pattern: Unclosed character class near index 3
```

Also an error: `@Validated` on an interface, annotation class or value class,
and a `@Validated` class that declares or inherits a `validate()` (it would
clash with the generated private one), and `@Validate` on a type that isn't a
`@Validated` class (or a collection or array of one). Constraints in a class
without `@Validated` are a warning (they'd have no effect).

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
| `plugin.validated.ir` | IR: reads the annotations, reports misuse, builds the checks, wires them into constructors and setters (`Elements.kt`: element types of collections and arrays; `Bridges.kt`: the hidden entry point used by `@Validate`) |
| `plugin.util.ir` | reusable IR helpers (`addCallAtStart`, ...) |

## Supported annotations

All in the `annotations` module (`io.github.abdullahkhan118.annotations`).
They can only be placed on properties (including `val`/`var` constructor parameters).
"Applies to" includes subtypes, and collections or arrays of those types (each
element is checked).

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
| `@Regex(pattern)` | `CharSequence` (`String`, `StringBuilder`, ...) | the whole value matches `pattern` |
| `@Validate` | a `@Validated` class (or subclass of one) | the object passes its own checks |

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

## Tests

```bash
./gradlew :compiler-plugin:test
```

The tests (`compiler-plugin/src/test`) compile small Kotlin sources in-process
with the plugin jar loaded via `-Xplugin`, as a real build does
(`TestCompiler.kt`). They check the compile errors and warnings, and run the
compiled code to check what is accepted and rejected (`ConstraintsTest`,
`CollectionsTest`, `LifecycleTest`, `NestedTest`, `DiagnosticsTest`).
`NestedTest` also compiles two "modules", one using the other's classes.

## Requirements

- JDK 17 (configured via `jvmToolchain(17)` in each module).
- The compiler plugin uses the Kotlin compiler's internal APIs, so it must be
  built with exactly the same Kotlin version as the code it compiles
  (`kotlin` in `gradle/libs.versions.toml`, currently 2.4.10).

## Known limitations

- `validate()` is private, so there's no way to re-check an object on demand.
  Code that creates objects without calling a constructor (reflection, some
  deserializers) or writes fields directly isn't checked at all.
- `@Regex` builds a new `Regex` for each check (for a collection: per element).
- Changing a collection in place (`team.ratings.add(9)` on a `MutableList`)
  doesn't go through a setter, so it isn't checked.
- Nested collections (`List<List<Int>>`) and `Map`s aren't supported: the
  constraint is a compile error there.

## Troubleshooting

If a build fails with `Operation timed out` while reading a jar under
`build/`, macOS iCloud ("Optimize Mac Storage") may have offloaded that file
(`ls -lO` shows `dataless`). Delete the `build/` directories and build again.
