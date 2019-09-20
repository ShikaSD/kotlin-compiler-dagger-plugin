package me.shika.di.resolver

import me.shika.di.model.Binding
import me.shika.di.model.GraphNode
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.types.KotlinType

fun ResolverContext.resolveGraph(componentDescriptor: ComponentDescriptor): GraphNode =
    GraphResolver(this, componentDescriptor).resolve()


class GraphResolver(
    private val resolverContext: ResolverContext,
    private val componentDescriptor: ComponentDescriptor
) {
    private val resolveMemoized = resolverContext.storageManager.createMemoizedFunction { it: KotlinType ->
        it.resolve()
    }

    fun resolve() =
        resolveMemoized(componentDescriptor.returnType)

    private fun KotlinType.resolve(): GraphNode {
        val binding = componentDescriptor.bindings.firstOrNull { it.type == this }
            ?: constructor()
            ?: TODO("Cannot find binding for $this")

        val bindingDeps = when (binding) {
            is Binding.Instance -> emptyList()
            is Binding.Function -> binding.from
            is Binding.Constructor -> binding.from
        }

        val children = bindingDeps.map { resolveMemoized(it) }
        return GraphNode(binding, children)
    }

    private fun KotlinType.constructor() = classDescriptor()
        ?.constructors
        ?.single { it.visibility == Visibilities.PUBLIC || it.visibility == Visibilities.INTERNAL }
        ?.let { Binding.Constructor(it.valueParameters.map { it.type }, this) }

}
