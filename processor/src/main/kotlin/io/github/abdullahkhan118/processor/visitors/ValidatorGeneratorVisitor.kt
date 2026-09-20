package io.github.abdullahkhan118.processor.visitors

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import io.github.abdullahkhan118.annotations.ByteRange
import io.github.abdullahkhan118.annotations.DoubleRange
import io.github.abdullahkhan118.annotations.FloatRange
import io.github.abdullahkhan118.annotations.IntRange
import io.github.abdullahkhan118.annotations.LongRange
import io.github.abdullahkhan118.annotations.ShortRange
import io.github.abdullahkhan118.processor.utils.appendToFile
import io.github.abdullahkhan118.processor.utils.declareAsVariable
import io.github.abdullahkhan118.processor.utils.getDeclaredName
import io.github.abdullahkhan118.processor.utils.getQualifiedName
import io.github.abdullahkhan118.processor.utils.getTypeQualifiedName
import io.github.abdullahkhan118.processor.utils.hasAnnotation
import kotlin.sequences.firstOrNull

class ValidatorGeneratorVisitor(
    private val codeGenerator: CodeGenerator
) : KSVisitorVoid() {

    private val validatorFunctionCalls = StringBuilder()
    private val validatorFunctions = StringBuilder()

    override fun visitAnnotated(annotated: KSAnnotated, data: Unit) {
        super.visitAnnotated(annotated, data)
    }

    override fun visitAnnotation(annotation: KSAnnotation, data: Unit) {
        super.visitAnnotation(annotation, data)

    }


    // Fix 3: the old `if (validatorFunctions.isNotEmpty()) return` guard made this
    // method a no-op after the FIRST property it ever successfully processed for
    // any class, since `validatorFunctions` is never cleared between properties -
    // every later property in the same class (and in every class after it, since
    // the visitor instance is shared for that class's whole traversal) was skipped.
    // Removed entirely: every property should be evaluated independently.
    @OptIn(KspExperimental::class)
    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
        super.visitPropertyDeclaration(property, data)
        val className = (property.parent as KSClassDeclaration).getDeclaredName()

        val objectVariableName = className
            .replaceFirst(className.first(), className.first().lowercaseChar())
        
        val functionDeclaration =
            "${property.getDeclaredName()}IsValid(${property.getDeclaredName()}: ${property.getTypeQualifiedName()})"
        
        val functionCall =
            "${property.getDeclaredName()}IsValid($objectVariableName.${property.getDeclaredName()})"

        when {
            // Fix 2: this branch used to only validate min <= max and then throw the
            // result of `.let { }` away - it never built `functionDeclaration` /
            // `validatorFunction` or appended them, so a @ByteRange property (like
            // User.age) silently produced no validator at all. Brought in line with
            // the other range branches below.
            
            property.hasAnnotation(ByteRange::class.java) -> property.getAnnotationsByType(ByteRange::class)
                .firstOrNull()?.let {
                    val min = it.min
                    val max = it.max
                    if (min > max) throw IllegalStateException("${property.getQualifiedName()} has min value greater than the max value")

                    val validatorFunction = """
                   private fun $functionDeclaration = if(${property.declareAsVariable()} < $min || ${property.declareAsVariable()} > $max ) throw IllegalArgumentException("${property.getQualifiedName()} is out of range") else true
                """.trimIndent()

                    validatorFunctionCalls.append(functionCall)
                    validatorFunctions.append(validatorFunction)
                }

            property.hasAnnotation(IntRange::class.java) -> property.getAnnotationsByType(
                IntRange::class
            )
                .firstOrNull()?.let {
                    val min = it.min
                    val max = it.max
                    if (min > max) throw IllegalStateException("${property.getQualifiedName()} has min value greater than the max value")
                    
                    val validatorFunction = """
                   private fun $functionDeclaration = if(${property.declareAsVariable()} < $min || ${property.declareAsVariable()} > $max ) throw IllegalArgumentException("${property.getQualifiedName()} is out of range") else true 
                """.trimIndent()

                    validatorFunctionCalls.append(functionCall)
                    validatorFunctions.append(validatorFunction)

                }

            property.hasAnnotation(ShortRange::class.java) -> property.getAnnotationsByType(ShortRange::class)
                .firstOrNull()?.let {
                    val min = it.min
                    val max = it.max
                    if (min > max) throw IllegalStateException("${property.getQualifiedName()} has min value greater than the max value")
                    

                    val validatorFunction = """
                   private fun $functionDeclaration = if(${property.declareAsVariable()} < $min || ${property.declareAsVariable()} > $max ) throw IllegalArgumentException("${property.getQualifiedName()} is out of range") else true 
                """.trimIndent()

                    validatorFunctionCalls.append(functionCall)
                    validatorFunctions.append(validatorFunction)
                }

            property.hasAnnotation(LongRange::class.java) -> property.getAnnotationsByType(LongRange::class)
                .firstOrNull()?.let {
                    val min = it.min
                    val max = it.max
                    if (min > max) throw IllegalStateException("${property.getQualifiedName()} has min value greater than the max value")
                    

                    val validatorFunction = """
                   private fun $functionDeclaration = if(${property.declareAsVariable()} < $min || ${property.declareAsVariable()} > $max ) throw IllegalArgumentException("${property.getQualifiedName()} is out of range") else true 
                """.trimIndent()

                    validatorFunctionCalls.append(functionCall)
                    validatorFunctions.append(validatorFunction)
                }

            property.hasAnnotation(FloatRange::class.java) -> property.getAnnotationsByType(FloatRange::class)
                .firstOrNull()?.let {
                    val min = it.min
                    val max = it.max
                    if (min > max) throw IllegalStateException("${property.getQualifiedName()} has min value greater than the max value")


                    val functionDeclaration =
                        "${property.getDeclaredName()}IsValid(${property.getDeclaredName()}: ${property.getTypeQualifiedName()})"

                    val validatorFunction = """
                   private fun $functionDeclaration = if(${property.declareAsVariable()} < $min || ${property.declareAsVariable()} > $max ) throw IllegalArgumentException("${property.getQualifiedName()} is out of range") else true 
                """.trimIndent()

                    validatorFunctionCalls.append(functionCall)
                    validatorFunctions.append(validatorFunction)
                }

            property.hasAnnotation(DoubleRange::class.java) -> property.getAnnotationsByType(DoubleRange::class)
                .firstOrNull()?.let {
                    val min = it.min
                    val max = it.max
                    if (min > max) throw IllegalStateException("${property.getQualifiedName()} has min value greater than the max value")


                    val functionDeclaration =
                        "${property.getDeclaredName()}IsValid(${property.getDeclaredName()}: ${property.getTypeQualifiedName()})"

                    val validatorFunction = """
                   private fun $functionDeclaration = if(${property.declareAsVariable()} < $min || ${property.declareAsVariable()} > $max ) throw IllegalArgumentException("${property.getQualifiedName()} is out of range") else true 
                """.trimIndent()

                    validatorFunctionCalls.append(functionCall)
                    validatorFunctions.append(validatorFunction)
                }

            else -> ""
        }
    }

    @OptIn(KspExperimental::class)
    override fun visitClassDeclaration(declaration: KSClassDeclaration, data: Unit) {
        super.visitClassDeclaration(declaration, data)
        
        declaration.getAllProperties().forEach { property -> property.accept(this, data) }

        val objectVariableName = declaration.getDeclaredName()
            .replaceFirst(declaration.getDeclaredName().first(), declaration.getDeclaredName().first().lowercaseChar())

        appendToFile(
            codeGenerator = codeGenerator, declaration = declaration, """
            class ${declaration.getDeclaredName()}Validator(
            private val $objectVariableName: ${declaration.getQualifiedName()}
            ){
                fun validate() {
                                    ${validatorFunctionCalls.toString()}
                }
                ${validatorFunctions.toString()}
            }
        """.trimIndent()
        )


    }

}


