package io.github.abdullahkhan118.plugin.validated.ir

import io.github.abdullahkhan118.plugin.validated.ValidatedNames
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameterWithClassParent
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnnotationClass
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/*
 * The bridge: how a @Validate property gets another class's checks run, although validate() is private.
 *
 * Every @Validated class also gets
 *
 *     /* synthetic */ open fun `validate$validated`() { super.`validate$validated`(); validate() }
 *
 * (without the super call in the topmost @Validated class of a hierarchy). It's public in the bytecode, so
 * code in other classes and modules can call it, but it's hidden from source: it's not in the Kotlin metadata
 * (Kotlin can't see it) and it's marked ACC_SYNTHETIC (javac refuses to call it). Being open, a call on an
 * Animal that is really a Spider runs Spider's bridge, which checks Animal's properties and then Spider's.
 *
 * A class compiled in another module has its bridge already (it was compiled with the plugin), but it isn't
 * visible in IR, so calls to it go through a stand-in declaration with the same JVM name and signature.
 */

/** Marks the bridge so the JVM backend emits it with ACC_SYNTHETIC. */
private val BRIDGE_ORIGIN = IrDeclarationOriginImpl("VALIDATED_BRIDGE", isSynthetic = true)

/** True if @Validated can be applied to this class (an error is reported for the rest). */
fun IrClass.canBeValidated(): Boolean = !isInterface && !isAnnotationClass && !isValue

/** True if this class is annotated with @Validated (and of a kind it can be used on). */
fun IrClass.isValidated(): Boolean = canBeValidated() && hasAnnotation(FqName(ValidatedNames.ANNOTATION))

/** This class if it's @Validated, otherwise its nearest @Validated superclass, or null if there's none. */
fun IrClass.nearestValidated(): IrClass? = if (isValidated()) this else validatedSuperclass()

/** The nearest @Validated superclass (not this class itself), or null if there's none. */
fun IrClass.validatedSuperclass(): IrClass? = superTypes
    .mapNotNull { it.classOrNull?.owner }
    .firstOrNull { it.kind == ClassKind.CLASS }
    ?.nearestValidated()

/** The bridges of all @Validated classes in this module (declared up front), and stand-ins for other modules'. */
class Bridges(private val pluginContext: IrPluginContext) {
    private val bridges = mutableMapOf<IrClass, IrSimpleFunction>()

    /**
     * Declares the (still bodiless) bridge of [irClass], a @Validated class of this module. All are declared before
     * any class is transformed, so a class can call the bridge of one it comes before.
     */
    fun declare(irClass: IrClass) {
        bridges[irClass] = irClass.addFunction { setUp(irClass) }.withReceiver()
    }

    /** The bridge of [irClass], a @Validated class: its own if it's in this module, a stand-in otherwise. */
    fun of(irClass: IrClass): IrSimpleFunction = bridges.getOrPut(irClass) {
        pluginContext.irFactory.buildFun { setUp(irClass) }.also { it.parent = irClass }.withReceiver()
    }

    private fun IrFunctionBuilder.setUp(irClass: IrClass) {
        name = Name.identifier(ValidatedNames.BRIDGE)
        returnType = pluginContext.irBuiltIns.unitType
        visibility = DescriptorVisibilities.PUBLIC
        modality = if (irClass.modality == Modality.FINAL) Modality.FINAL else Modality.OPEN
        origin = BRIDGE_ORIGIN
    }

    private fun IrSimpleFunction.withReceiver() = apply { parameters = listOf(createDispatchReceiverParameterWithClassParent()) }
}
