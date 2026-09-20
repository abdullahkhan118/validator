package io.github.abdullahkhan118.processor.utils

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSDeclaration
import java.io.OutputStream

data class FileContent(
    val content: StringBuilder,
    val outputStream: OutputStream,
)


private val generatedFileAndContent = hashMapOf<String, FileContent>()


// 3️⃣ createNewFileFrom() function
fun createNewFileFrom(codeGenerator: CodeGenerator, declaration: KSDeclaration): OutputStream {
    return codeGenerator.createNewFile(
        dependencies = createDependencyOn(declaration),
        packageName = declaration.packageName.asString(),
        fileName = declaration.simpleName.asString() + "Generated"
    )
}

// 3️⃣ createNewFileFrom() function
fun createOrRecreateFile(
    codeGenerator: CodeGenerator,
    declaration: KSDeclaration
): FileContent {
    codeGenerator.generatedFile.forEach { println("Generated File ${it.name}") }
    val fileName = declaration.simpleName.asString().plus("Generated.kt")
    val outputStream = codeGenerator.generatedFile.firstOrNull {
        it.name.equals(fileName)
    }?.outputStream() ?: run {
        createNewFileFrom(codeGenerator, declaration)
    }
    val content =
        generatedFileAndContent.get(fileName)?.content ?: StringBuilder(addPackageDeclaration(declaration))
    val fileContent = FileContent(content, outputStream)
    generatedFileAndContent[fileName] = fileContent
    return fileContent
}

fun appendToFile(
    codeGenerator: CodeGenerator,
    declaration: KSDeclaration,
    content: String
) {
    val fileContent = createOrRecreateFile(codeGenerator, declaration)
    fileContent.content.appendLine(content)
    fileContent.outputStream.appendText(fileContent.content.toString())
}

// 3️⃣ createDependencyOn() function
fun createDependencyOn(function: KSDeclaration): Dependencies {
    return Dependencies(aggregating = false, function.containingFile!!)
}

// Utility function for writing string to OutputStream
fun OutputStream.appendText(string: String) {
    bufferedWriter().append(string).flush()
}