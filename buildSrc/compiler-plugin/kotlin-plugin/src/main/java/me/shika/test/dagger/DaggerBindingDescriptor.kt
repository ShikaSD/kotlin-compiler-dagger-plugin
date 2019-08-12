package me.shika.test.dagger

import me.shika.test.model.Binding
import me.shika.test.model.Endpoint
import me.shika.test.model.Injectable
import me.shika.test.resolver.classDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

class DaggerBindingDescriptor(
    val componentDescriptor: DaggerComponentDescriptor
) {
    val bindings = findBindings()
    val endpoints = findEndpoints()

    private fun findBindings(): List<Binding> {
        val modules = componentDescriptor.modules
        val instances = componentDescriptor.moduleInstances
        return modules.flatMap { module ->
            val companionBindings = module.companionObjectDescriptor
                ?.allDescriptors(DescriptorKindFilter.FUNCTIONS)
                ?: emptyList()

            (module.allDescriptors(DescriptorKindFilter.FUNCTIONS) + companionBindings)
                .filter { it.annotations.hasAnnotation(PROVIDES_FQ_NAME) }
                .filterIsInstance<FunctionDescriptor>()
                .map {
                    when (module) {
                        in instances -> Binding.InstanceFunction(module, it, it.scopeAnnotations())
                        else -> Binding.StaticFunction(it, it.scopeAnnotations())
                    }
                }
        }
    }

    private fun findEndpoints(): List<Endpoint> {
        val scope = componentDescriptor.definition.unsubstitutedMemberScope
        val functions = scope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS)
            .asSequence()
            .filterIsInstance<FunctionDescriptor>()
            .filter { !it.builtIns.isMemberOfAny(it) }

        val exposedTypes = functions.getExposedTypes()
        val injectables = functions.getInjectedTypes()

        return (exposedTypes + injectables).toList()
    }

    private fun Sequence<FunctionDescriptor>.getExposedTypes() =
        filter { it.valueParameters.isEmpty() }
            .map { Endpoint.Exposed(it) }

    private fun Sequence<FunctionDescriptor>.getInjectedTypes() =
        filter { it.valueParameters.isNotEmpty() }
            .flatMap { func ->
                val cls = func.valueParameters.first().type.classDescriptor()!!
                val fields =
                    cls.allDescriptors(DescriptorKindFilter.VARIABLES)
                        .asSequence()
                        .filterIsInstance<PropertyDescriptor>()
                        .filter { it.backingField?.annotations?.hasAnnotation(INJECT_FQ_NAME) == true }
                        .map {
                            Endpoint.Injected(func, Injectable.Property(cls, it))
                        }

                val setters = cls.allDescriptors(DescriptorKindFilter.FUNCTIONS)
                    .asSequence()
                    .filterIsInstance<FunctionDescriptor>()
                    .filter { cls.annotations.hasAnnotation(INJECT_FQ_NAME) }
                    .map {
                        Endpoint.Injected(func, Injectable.Setter(cls, it))
                    }

                fields + setters
            }
}

val INJECT_FQ_NAME = FqName("javax.inject.Inject")
val PROVIDES_FQ_NAME = FqName("dagger.Provides")

private fun ClassDescriptor.allDescriptors(kindFilter: DescriptorKindFilter) =
    unsubstitutedMemberScope.getDescriptorsFiltered(kindFilter) { true }
