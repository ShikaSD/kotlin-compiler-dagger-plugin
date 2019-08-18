package me.shika.test.dagger

import me.shika.test.model.Binding
import me.shika.test.model.Endpoint
import me.shika.test.model.Injectable
import me.shika.test.resolver.classDescriptor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.contracts.parsing.isEqualsDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.typeUtil.isInt

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

    private fun findEndpoints(): Set<Endpoint> {
        val scope = componentDescriptor.definition.unsubstitutedMemberScope
        val functions = scope.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS)
            .asSequence()
            .filterIsInstance<FunctionDescriptor>()
            .filter {
                !it.isEqualsDescriptor() && !it.isHashCodeDescriptor() && !it.isToStringDescriptor()
            }

        // TODO go through each instead of filtering

        val exposedTypes = functions.getExposedTypes()
        val injectables = functions.getInjectedTypes()

        return (exposedTypes + injectables).toSet()
    }

    private fun Sequence<FunctionDescriptor>.getExposedTypes() =
        filter { it.valueParameters.isEmpty() }
            .map { Endpoint.Exposed(it) }

    private fun Sequence<FunctionDescriptor>.getInjectedTypes() =
        filter { it.valueParameters.size == 1 && KotlinBuiltIns.isUnit(it.returnType!!)  }
            .flatMap { func ->
                val cls = func.valueParameters.first().type.classDescriptor()!!
                val fields =
                    cls.allDescriptors(DescriptorKindFilter.VARIABLES)
                        .asSequence()
                        .filterIsInstance<PropertyDescriptor>()
                        .filter {
                            it.annotations.hasAnnotation(INJECT_FQ_NAME) ||
                                it.backingField?.annotations?.hasAnnotation(INJECT_FQ_NAME) == true ||
                                it.setter?.annotations?.hasAnnotation(INJECT_FQ_NAME) == true
                        }
                        .map {
                            Endpoint.Injected(func, Injectable.Property(cls, it))
                        }

                val setters = cls.allDescriptors(DescriptorKindFilter.FUNCTIONS)
                    .asSequence()
                    .filterIsInstance<FunctionDescriptor>()
                    .filter { it.annotations.hasAnnotation(INJECT_FQ_NAME) }
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

private fun DeclarationDescriptor.isHashCodeDescriptor() =
    this is FunctionDescriptor && name == Name.identifier("hashCode")
        && returnType?.isInt() == true && valueParameters.isEmpty()

private fun DeclarationDescriptor.isToStringDescriptor() =
    this is FunctionDescriptor && name == Name.identifier("toString")
        && KotlinBuiltIns.isString(returnType) && valueParameters.isEmpty()
