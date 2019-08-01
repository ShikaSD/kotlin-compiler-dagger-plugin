package me.shika.test

import me.shika.test.ir.ComponentIrGenerator
import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.resolve.BindingContext

class TestCompilerIrGeneration(val reporter: MessageCollector): IrGenerationExtension {
    override fun generate(file: IrFile, backendContext: BackendContext, bindingContext: BindingContext) {
        file.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                generate(declaration, backendContext, bindingContext)
                declaration.acceptChildrenVoid(this)
            }

        })
    }

    private fun generate(irClass: IrClass, backendContext: BackendContext, bindingContext: BindingContext) {
        reporter.warn("Calling generator for ${irClass.descriptor}")

        val descriptor = irClass.descriptor

        val moduleInstances = bindingContext[DAGGER_MODULE_INSTANCES, descriptor] ?: return
        val resolveResult = bindingContext[DAGGER_RESOLUTION_RESULT, descriptor] ?: return
        val scopedBindings = bindingContext[DAGGER_SCOPED_BINDINGS, descriptor] ?: return

        reporter.warn("Generating for ${irClass.descriptor}")

        val generator = ComponentIrGenerator(
            irClass = irClass,
            moduleInstances = moduleInstances,
            resolveResult = resolveResult,
            scopedBindings = scopedBindings,
            backendContext = backendContext
        )

        generator.generateFields()
        generator.generateConstructor()
        generator.generateBindings()
    }

    object DiOrigin : IrDeclarationOriginImpl("DI")
}
