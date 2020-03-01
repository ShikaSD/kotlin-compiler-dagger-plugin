package me.shika.di.dagger.resolver.endpoints

import me.shika.di.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.di.dagger.resolver.INJECT_FQ_NAME
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.allDescriptors
import me.shika.di.dagger.resolver.classDescriptor
import me.shika.di.dagger.resolver.isFromAny
import me.shika.di.dagger.resolver.qualifiers
import me.shika.di.model.Endpoint
import me.shika.di.model.Injectable
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.FUNCTIONS
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.VARIABLES

class InjectionEndpointResolver(
    componentAnnotation: ComponentAnnotationDescriptor,
    private val definition: ClassDescriptor,
    private val context: ResolverContext
): EndpointResolver {
    // TODO: validate

    override fun invoke(): List<Endpoint> {
        val componentFunctions = definition.allDescriptors(FUNCTIONS)
            .asSequence()
            .filterIsInstance<FunctionDescriptor>()
            .filterNot { it.isFromAny() }

        return componentFunctions.filter { it.isInjection() }
            .flatMap { func ->
                val cls = func.valueParameters.first().type.classDescriptor()!!
                val fields = cls.findInjectedFields()
                    .map {
                        Endpoint.Injected(func, Injectable.Property(cls, it), it.qualifiers())
                    }

                val setters = cls.findInjectedSetters()
                    .map {
                        Endpoint.Injected(func, Injectable.Setter(cls, it), it.qualifiers())
                    }

                fields + setters
            }
            .toList()
    }

    private fun FunctionDescriptor.isInjection() =
        valueParameters.size == 1 &&
            (KotlinBuiltIns.isUnit(returnType!!) || returnType == valueParameters.first().returnType)

    private fun ClassDescriptor.findInjectedFields() =
        allDescriptors(VARIABLES)
            .asSequence()
            .filterIsInstance<PropertyDescriptor>()
            .filter {
                it.annotations.hasAnnotation(INJECT_FQ_NAME) ||
                        it.backingField?.annotations?.hasAnnotation(INJECT_FQ_NAME) == true ||
                        it.setter?.annotations?.hasAnnotation(INJECT_FQ_NAME) == true
            }

    private fun ClassDescriptor.findInjectedSetters() =
        allDescriptors(FUNCTIONS)
            .asSequence()
            .filterIsInstance<FunctionDescriptor>()
            .filter { it.annotations.hasAnnotation(INJECT_FQ_NAME) }
}
