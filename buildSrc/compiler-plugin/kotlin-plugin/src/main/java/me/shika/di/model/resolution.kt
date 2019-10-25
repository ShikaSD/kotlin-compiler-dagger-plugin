package me.shika.di.model

import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.types.KotlinType

data class GraphNode(val value: Binding, val dependencies: List<GraphNode>)

data class ResolveResult(val endpoint: Endpoint, val graph: List<GraphNode>)

sealed class Endpoint {
    abstract val source: FunctionDescriptor
    abstract val qualifiers: List<AnnotationDescriptor>

    data class Provided(override val source: FunctionDescriptor, override val qualifiers: List<AnnotationDescriptor>) : Endpoint()
    data class Injected(
        override val source: FunctionDescriptor,
        val value: Injectable,
        override val qualifiers: List<AnnotationDescriptor>
    ) : Endpoint()

    val types get() = when (this) {
        is Injected -> value.types
        is Provided -> listOf(source.returnType)
    }
}

sealed class Injectable {
    data class Setter(val cls: ClassDescriptor, val descriptor: FunctionDescriptor): Injectable()
    data class Property(val cls: ClassDescriptor, val descriptor: PropertyDescriptor): Injectable()

    val types get() = when(this) {
        is Setter -> descriptor.valueParameters.map { it.type }
        is Property -> listOf(descriptor.returnType)
    }
}

data class Key(
    val type: KotlinType,
    val qualifiers: List<AnnotationDescriptor> = emptyList()
)

data class Binding(
    val key: Key,
    val scopes: List<AnnotationDescriptor>,
    val bindingType: Variation
) {
    sealed class Variation {
        abstract val source: DeclarationDescriptor

        data class Constructor(override val source: ClassConstructorDescriptor): Variation()
        data class InstanceFunction(override val source: FunctionDescriptor): Variation()
        data class InstanceProperty(override val source: PropertyDescriptor): Variation()
        data class StaticFunction(override val source: FunctionDescriptor): Variation()
        data class Equality(override val source: FunctionDescriptor): Variation()
        data class BoundInstance(override val source: ValueParameterDescriptor): Variation()
        data class Component(override val source: ClassDescriptor) : Variation()
    }
}
