package io.github.abdullahkhan118.plugin

import io.github.abdullahkhan118.annotations.Validated
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

/*
 * Compiles Kotlin source in-process with the validator plugin loaded from its jar (as `-Xplugin`, like a real
 * build), so tests can check both the plugin's diagnostics and the behaviour of the code it generates.
 */

/** The plugin jar, passed in by the Gradle test task. */
private val pluginJar: String = System.getProperty("validator.pluginJar")
    ?: error("validator.pluginJar not set: run the tests through Gradle")

/** Where a class was loaded from: a jar or a classes directory. */
private fun locationOf(type: Class<*>): File = File(type.protectionDomain.codeSource.location.toURI())

/** Compiler diagnostics of one severity, as `line:column message`, without the `[validated] ` prefix. */
class Compilation(
    val exitCode: ExitCode,
    val errors: List<String>,
    val warnings: List<String>,
    private val classes: File,
) {
    /** Loads the compiled classes; the annotations and the stdlib come from the test's own class path. */
    val classLoader: ClassLoader by lazy { URLClassLoader(arrayOf(classes.toURI().toURL()), javaClass.classLoader) }

    /** Calls the top-level function [name] in `Source.kt`, rethrowing what it throws. */
    fun call(name: String): Any? {
        val method = classLoader.loadClass("SourceKt").getMethod(name)
        try {
            return method.invoke(null)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    /** Asserts that calling [name] throws IllegalArgumentException with [message]. */
    fun assertRejects(name: String, message: String) {
        val e = assertFailsWith<IllegalArgumentException>("$name should have been rejected") { call(name) }
        assertEquals(message, e.message, name)
    }
}

/** Compiles [source] (one file, `Source.kt`, no package) with the plugin. */
fun compile(source: String): Compilation {
    val dir = Files.createTempDirectory("validator-test").toFile()
    val sourceFile = File(dir, "Source.kt").apply { writeText(source.trimIndent()) }
    val out = File(dir, "classes")

    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val collector = object : MessageCollector {
        override fun clear() {}
        override fun hasErrors() = errors.isNotEmpty()
        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
            val text = (location?.let { "${it.line}:${it.column} " } ?: "") + message.removePrefix("[validated] ")
            when {
                severity.isError -> errors += text
                severity.isWarning -> warnings += text
            }
        }
    }
    val arguments = K2JVMCompilerArguments().apply {
        freeArgs = listOf(sourceFile.absolutePath)
        destination = out.absolutePath
        classpath = listOf(locationOf(Validated::class.java), locationOf(Unit::class.java)).joinToString(File.pathSeparator)
        pluginClasspaths = arrayOf(pluginJar)
        noStdlib = true
        noReflect = true
        jvmTarget = "17"
    }
    val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
    return Compilation(exitCode, errors, warnings, out)
}

/** Compiles [source], failing the test if it doesn't compile. */
fun compileOk(source: String): Compilation {
    val result = compile(source)
    if (result.exitCode != ExitCode.OK) fail("Compilation failed:\n" + result.errors.joinToString("\n"))
    return result
}
