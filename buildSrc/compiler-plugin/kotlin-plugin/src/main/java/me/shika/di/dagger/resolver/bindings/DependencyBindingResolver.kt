package me.shika.di.dagger.resolver.bindings

import me.shika.di.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.allDescriptors
import me.shika.di.dagger.resolver.isFromAny
import me.shika.di.dagger.resolver.qualifiers
import me.shika.di.dagger.resolver.scopeAnnotations
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.InstanceFunction
import me.shika.di.model.Binding.Variation.InstanceProperty
import me.shika.di.model.Key
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

class DependencyBindingResolver(
    private val componentAnnotation: ComponentAnnotationDescriptor,
    private val definition: ClassDescriptor,
    private val context: ResolverContext
): BindingResolver {
    override fun invoke(): List<Binding> =
        componentAnnotation.dependencies
            .flatMap { resolveBindings(it) }

    private fun resolveBindings(dependency: ClassDescriptor): List<Binding> =
        resolveDependencyFunctions(dependency) + resolveDependencyProperties(dependency)

    private fun resolveDependencyFunctions(dependency: ClassDescriptor): List<Binding> =
        dependency.allDescriptors(DescriptorKindFilter.FUNCTIONS)
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

    private fun resolveDependencyProperties(dependency: ClassDescriptor): List<Binding> =
        dependency.allDescriptors(DescriptorKindFilter.VARIABLES)
            .asSequence()
            .filterIsInstance<PropertyDescriptor>()
            .map {
                Binding(
                    Key(it.returnType!!, it.qualifiers()),
                    it.scopeAnnotations(),
                    InstanceProperty(it)
                )
            }
            .toList()
}

