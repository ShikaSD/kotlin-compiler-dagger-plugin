package me.shika.test.dagger.resolver.bindings

import me.shika.test.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.test.dagger.resolver.ResolverContext
import me.shika.test.dagger.resolver.allDescriptors
import me.shika.test.dagger.resolver.isFromAny
import me.shika.test.dagger.resolver.qualifiers
import me.shika.test.dagger.resolver.scopeAnnotations
import me.shika.test.model.Binding
import me.shika.test.model.Binding.Variation.InstanceFunction
import me.shika.test.model.Key
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

class DependencyBindingResolver(
    private val componentAnnotation: ComponentAnnotationDescriptor,
    private val definition: ClassDescriptor,
    private val context: ResolverContext
): BindingResolver {
    override fun invoke(): List<Binding> =
        componentAnnotation.dependencies
            .flatMap { resolveBindings(it) }

    private fun resolveBindings(dependency: ClassDescriptor): List<Binding> {
        return dependency.allDescriptors(DescriptorKindFilter.FUNCTIONS)
            .asSequence()
            .filterIsInstance<FunctionDescriptor>()
            .filterNot { it.isFromAny() }
            .filter { it.valueParameters.isEmpty() }
            .map {
                Binding(
                    Key(it.returnType!!, it.qualifiers()),
                    it.scopeAnnotations(),
                    InstanceFunction(it)
                )
            }
            .toList()
    }
}

