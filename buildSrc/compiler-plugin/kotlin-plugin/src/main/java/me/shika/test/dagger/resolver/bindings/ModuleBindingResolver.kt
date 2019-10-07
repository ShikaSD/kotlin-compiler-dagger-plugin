package me.shika.test.dagger.resolver.bindings

import me.shika.test.MODULE_WITHOUT_ANNOTATION
import me.shika.test.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.test.dagger.resolver.ResolverContext
import me.shika.test.dagger.resolver.allDescriptors
import me.shika.test.dagger.resolver.qualifiers
import me.shika.test.dagger.resolver.report
import me.shika.test.dagger.resolver.scopeAnnotations
import me.shika.test.model.Binding
import me.shika.test.model.Key
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

class ModuleBindingResolver(
    private val componentAnnotation: ComponentAnnotationDescriptor,
    private val definition: ClassDescriptor,
    private val context: ResolverContext
): BindingResolver {
    override fun invoke(): List<Binding> =
        componentAnnotation.modules
            .flatMap { resolveBindings(it) }

    private fun resolveBindings(module: ClassDescriptor): List<Binding> {
        module.validateModuleAnnotation()

        val isInstance = module in componentAnnotation.moduleInstances

        val companionFunctions = module.companionObjectDescriptor
            ?.allDescriptors(DescriptorKindFilter.FUNCTIONS)
            ?: emptyList()

        val moduleFunctions = module.allDescriptors(DescriptorKindFilter.FUNCTIONS)

        return (moduleFunctions + companionFunctions)
            .filterIsInstance<FunctionDescriptor>()
            .filter { it.annotations.hasAnnotation(PROVIDES_FQ_NAME) }
            .map {
                Binding(
                    Key(it.returnType!!, it.qualifiers()),
                    it.scopeAnnotations(),
                    when {
                        isInstance -> Binding.Variation.InstanceFunction(it)
                        else -> Binding.Variation.StaticFunction(it)
                    }
                )
            }
    }

    private fun ClassDescriptor.validateModuleAnnotation() {
        if (!annotations.hasAnnotation(DAGGER_MODULE_FQ_NAME)) {
            report(context.trace) { MODULE_WITHOUT_ANNOTATION.on(it, definition) }
        }
    }
}

private val PROVIDES_FQ_NAME = FqName("dagger.Provides")
private val DAGGER_MODULE_FQ_NAME = FqName("dagger.Module")
