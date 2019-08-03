package me.shika.test.model

import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.Name

data class ResolveResult(val endpoint: Endpoint, val bindings: Set<Binding>)

sealed class Endpoint {
    data class Exposed(val source: FunctionDescriptor) : Endpoint()
    data class Injected(val source: FunctionDescriptor, val value: Injectable) : Endpoint()

    val type get() = when (this) {
        is Injected -> value.type
        is Exposed -> source.returnType
    }
}

sealed class Injectable {
    data class Setter(val cls: ClassDescriptor, val descriptor: FunctionDescriptor): Injectable()
    data class Property(val cls: ClassDescriptor, val descriptor: PropertyDescriptor): Injectable()

    val type get() = when(this) {
        is Setter -> descriptor.returnType
        is Property -> descriptor.returnType
    }
}

sealed class Binding {
    data class InstanceFunction(val moduleInstance: ClassDescriptor, val descriptor: FunctionDescriptor): Binding()
    data class StaticFunction(val descriptor: FunctionDescriptor): Binding()
    data class Constructor(val descriptor: ClassConstructorDescriptor): Binding()

    val resolvedDescriptor get() = when (this) {
        is InstanceFunction -> descriptor
        is StaticFunction -> descriptor
        is Constructor -> descriptor
    }

    val type get() = resolvedDescriptor.returnType

    val providerName get() = when (this) {
        is InstanceFunction,
        is StaticFunction -> resolvedDescriptor.name
        is Constructor -> descriptor.containingDeclaration.name
    }.toProviderName()

    private fun Name.toProviderName() = Name.identifier(asString().capitalize() + "Provider")
}
