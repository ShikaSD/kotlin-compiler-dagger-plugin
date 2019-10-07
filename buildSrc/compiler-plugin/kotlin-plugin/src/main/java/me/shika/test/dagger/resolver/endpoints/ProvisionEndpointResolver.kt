package me.shika.test.dagger.resolver.endpoints

import me.shika.test.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.test.dagger.resolver.ResolverContext
import me.shika.test.dagger.resolver.allDescriptors
import me.shika.test.dagger.resolver.isFromAny
import me.shika.test.model.Endpoint
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.FUNCTIONS

class ProvisionEndpointResolver(
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

        return componentFunctions
            .filter { it.isProvisionEndpoint() }
            .map { Endpoint.Provided(it) }
            .toList()
    }

    private fun FunctionDescriptor.isProvisionEndpoint() =
        valueParameters.isEmpty() && !KotlinBuiltIns.isUnit(returnType!!)
}
