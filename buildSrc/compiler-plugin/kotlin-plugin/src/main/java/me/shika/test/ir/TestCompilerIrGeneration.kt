package me.shika.test.ir

import me.shika.test.DAGGER_MODULE_INSTANCES
import me.shika.test.DAGGER_RESOLUTION_RESULT
import me.shika.test.scopeAnnotations
import me.shika.test.warn
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
        // TODO Cache in declaration checker
        val scopedBindings = resolveResult.flatMap { it.second }
            .distinct()
            .filter { it.scopeAnnotations().isNotEmpty() }

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
