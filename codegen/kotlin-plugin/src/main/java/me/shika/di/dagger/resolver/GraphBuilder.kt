package me.shika.di.dagger.resolver

import me.shika.di.AMBIGUOUS_BINDINGS
import me.shika.di.BINDING_SCOPE_MISMATCH
import me.shika.di.NO_BINDINGS_FOUND
import me.shika.di.dagger.resolver.GraphNodeCache.CallParam
import me.shika.di.dagger.resolver.GraphNodeCache.Value.Computing
import me.shika.di.dagger.resolver.GraphNodeCache.Value.Created
import me.shika.di.dagger.resolver.GraphNodeCache.Value.Recursive
import me.shika.di.dagger.resolver.bindings.InjectConstructorBindingResolver
import me.shika.di.dagger.resolver.bindings.ProviderOrLazyBindingResolver
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation
import me.shika.di.model.Endpoint
import me.shika.di.model.GraphNode
import me.shika.di.model.Key
import me.shika.di.model.ResolveResult
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.types.KotlinType
import java.util.concurrent.ConcurrentHashMap

class GraphBuilder(
    private val context: ResolverContext,
    private val componentScopes: List<AnnotationDescriptor>,
    private val endpoints: List<Endpoint>,
    resolvedBindings: List<Binding>
) {
    private val cache = GraphNodeCache()
    private val trace = context.trace
    private val componentScopeFqNames = componentScopes.map { it.fqName }
    private val bindings = resolvedBindings.toMutableList()

    private val cachedNodeResolve = { type: KotlinType, source: DeclarationDescriptor, qualifiers: List<AnnotationDescriptor> ->
        cache.invoke(CallParam(type, source, qualifiers)) {
            it.type.resolveNode(it.source, it.qualifiers)
        }
    }

    fun build(): List<ResolveResult> = endpoints.map { it.resolveDependencies() }

    private fun Endpoint.resolveDependencies(): ResolveResult {
        val nodes = types.mapNotNull {
            try {
                cachedNodeResolve(it!!, source, qualifiers)
            } catch (e: BindingResolveException) {
                null
            }
        }

        return ResolveResult(this, nodes.resolveRecursiveConflicts())
    }

    private fun List<GraphNodeCache.Value>.resolveRecursiveConflicts(): List<GraphNode> =
        mapNotNull {
            when (it) {
                is Recursive -> {
                    val node = cache.resolve(it) ?: return@mapNotNull null
                    val param = it.key
                    GraphNode(
                        Binding(
                            Key(param.type, param.qualifiers),
                            emptyList(),
                            Variation.Recursive(node.binding.bindingType.source, node.binding)
                        ),
                        emptyList()
                    )
                }
                is Created -> GraphNode(it.node.binding, it.node.dependencies.resolveRecursiveConflicts())
                is Computing -> null
            }
        }

    private fun KotlinType.resolveNode(
        source: DeclarationDescriptor,
        qualifiers: List<AnnotationDescriptor>
    ): CachedGraphNode {
        val applicableBindings = bindings.filter {
            it.key.type applicableTo this && qualifiers.map { it.fqName } == it.key.qualifiers.map { it.fqName }
        }
        val bindings = if (applicableBindings.isEmpty()) {
            providerOrLazy(source, qualifiers).ifEmpty {
                injectableConstructor()
            }
        } else {
            applicableBindings
        }

        if (bindings.isEmpty()) {
            source.report(trace) {
                NO_BINDINGS_FOUND.on(it, this)
            }
            throw BindingResolveException()
        }

        if (bindings.size > 1) {
            source.report(trace) {
                AMBIGUOUS_BINDINGS.on(it, this, bindings.map { it.bindingType.source })
            }
            throw BindingResolveException()
        }

        val binding = bindings.first()

        if (!componentScopeFqNames.containsAll(binding.scopes.map { it.fqName })) {
            source.report(trace) {
                BINDING_SCOPE_MISMATCH.on(it, componentScopes, binding.scopes)
            }
            throw BindingResolveException()
        }

        return CachedGraphNode(binding, binding.resolveDependencies())
    }

    private fun Binding.resolveDependencies(): List<GraphNodeCache.Value> {
        val keys = when (val binding = bindingType) {
            is Variation.Constructor,
            is Variation.InstanceFunction,
            is Variation.Equality,
            is Variation.StaticFunction -> {
                (bindingType.source as FunctionDescriptor).valueParameters.map {
                    Key(it.type, it.qualifiers())
                }
            }
            is Variation.Lazy -> listOf(Key(binding.innerType, key.qualifiers))
            is Variation.Provider -> listOf(Key(binding.innerType, key.qualifiers))
            is Variation.BoundInstance,
            is Variation.InstanceProperty,
            is Variation.Recursive,
            is Variation.Component -> emptyList()
        }
        return keys.map { cachedNodeResolve(it.type, bindingType.source, it.qualifiers) }
    }

    private fun KotlinType.injectableConstructor(): List<Binding> =
        InjectConstructorBindingResolver(this, context).invoke().also {
            bindings += it
        }

    private fun KotlinType.providerOrLazy(source: DeclarationDescriptor, qualifiers: List<AnnotationDescriptor>): List<Binding> =
        ProviderOrLazyBindingResolver(this, source, qualifiers, context).invoke().also {
            bindings += it
        }

    private class BindingResolveException(message: String = "") : RuntimeException(message)

    private class UnwindStack(val items: MutableList<CallParam> = mutableListOf()): RuntimeException() {
        fun add(type: KotlinType, descriptor: DeclarationDescriptor, qualifiers: List<AnnotationDescriptor>) {
            items += CallParam(type, descriptor, qualifiers)
        }
    }
}

private data class CachedGraphNode(val binding: Binding, val dependencies: List<GraphNodeCache.Value>)

private class GraphNodeCache {
    data class CallParam(
        val type: KotlinType,
        val source: DeclarationDescriptor,
        val qualifiers: List<AnnotationDescriptor>
    )

    sealed class Value {
        object Computing : Value()
        data class Recursive(val key: Key): Value()
        data class Created(val node: CachedGraphNode) : Value()
    }

    private val map = ConcurrentHashMap<Key, Value>()

    operator fun invoke(param: CallParam, f: (CallParam) -> CachedGraphNode): Value {
        val key = Key(param.type, param.qualifiers)
        val value = map[key]
        return if (value != null) {
            when (value) {
                is Computing -> Recursive(key)
                is Recursive -> Recursive(key)
                is Created -> value
            }
        } else {
            map[key] = Computing
            val newValue = Created(f(param))
            map[key] = newValue
            newValue
        }
    }

    fun resolve(value: Recursive) =
        (map[value.key] as? Value.Created)?.node

    override fun toString(): String =
        map.toString()
}
