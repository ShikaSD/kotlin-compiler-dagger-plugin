package me.shika.test.dagger.resolver

import me.shika.test.AMBIGUOUS_BINDINGS
import me.shika.test.BINDING_SCOPE_MISMATCH
import me.shika.test.MORE_THAN_ONE_INJECT_CONSTRUCTOR
import me.shika.test.NO_BINDINGS_FOUND
import me.shika.test.model.Binding
import me.shika.test.model.Binding.Variation
import me.shika.test.model.Endpoint
import me.shika.test.model.GraphNode
import me.shika.test.model.Key
import me.shika.test.model.ResolveResult
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType

class GraphBuilder(
    private val context: ResolverContext,
    private val componentScopes: List<AnnotationDescriptor>,
    private val endpoints: List<Endpoint>,
    private val bindings: List<Binding>
) {
    private val storage = LockBasedStorageManager.NO_LOCKS
    private val trace = context.trace
    private val componentScopeFqNames = componentScopes.map { it.fqName }

    private val cachedNodeResolve = storage.createMemoizedFunction<CallParam, GraphNode> { (type, source, qualifiers) ->
        type.resolveNode(source, qualifiers)
    }

    fun build(): List<ResolveResult> = endpoints.map { it.resolveDependencies() }

    private fun Endpoint.resolveDependencies(): ResolveResult {
        val nodes = types.mapNotNull {
            try {
                cachedNodeResolve(CallParam(it!!, source, source.qualifiers()))
            } catch (e: Exception) {
                // TODO: record stack + error
                null
            }
        }
        return ResolveResult(this, nodes)
    }

    private fun KotlinType.resolveNode(source: DeclarationDescriptor, qualifiers: List<AnnotationDescriptor>): GraphNode {
        val provisionBindings = bindings.filter {
            this applicableTo it.key.type && qualifiers == it.key.qualifiers
        }
        val providers = if (provisionBindings.isEmpty()) {
            listOfNotNull(injectableConstructor())
        } else {
            provisionBindings
        }

        if (providers.isEmpty()) {
            source.report(trace) {
                NO_BINDINGS_FOUND.on(it, this)
            }
            throw RuntimeException()
        }

        if (providers.size > 1) {
            source.report(trace) {
                AMBIGUOUS_BINDINGS.on(it, this, providers.map { it.bindingType.source })
            }
            throw RuntimeException()
        }

        val binding = providers.first()

        if (!componentScopeFqNames.containsAll(binding.scopes.map { it.fqName })) {
            source.report(trace) {
                BINDING_SCOPE_MISMATCH.on(it, componentScopes, binding.scopes)
            }
            throw RuntimeException()
        }

        return GraphNode(binding, binding.resolveDependencies()) // TODO report recursive calls
    }

    private fun Binding.resolveDependencies(): List<GraphNode> {
        // TODO move outside
        val dependencyTypes = when (bindingType) {
            is Variation.Constructor,
            is Variation.InstanceFunction,
            is Variation.StaticFunction -> {
                (bindingType.source as FunctionDescriptor).valueParameters.mapNotNull { it.type }
            }
            is Variation.BoundInstance -> emptyList()
            is Variation.Component -> emptyList()
        }
        return dependencyTypes.map { cachedNodeResolve(CallParam(it, bindingType.source, key.qualifiers)) }
    }

    private fun KotlinType.injectableConstructor(): Binding? {
        val classDescriptor = classDescriptor() ?: return null
        val injectableConstructors = classDescriptor.constructors.filter { it.annotations.hasAnnotation(INJECT_FQ_NAME) }
        if (injectableConstructors.size > 1) {
            classDescriptor.report(trace) {
                MORE_THAN_ONE_INJECT_CONSTRUCTOR.on(it, classDescriptor)
            }
        }
        return injectableConstructors.firstOrNull()?.let {
            Binding(
                Key(this, emptyList()),
                classDescriptor.scopeAnnotations(),
                Variation.Constructor(it)
            )
        }
    }

    private data class CallParam(
        val type: KotlinType,
        val descriptor: DeclarationDescriptor,
        val qualifiers: List<AnnotationDescriptor>
    )

    private class UnwindStack(val items: MutableList<CallParam> = mutableListOf()): RuntimeException() {
        fun add(type: KotlinType, descriptor: DeclarationDescriptor, qualifiers: List<AnnotationDescriptor>) {
            items += CallParam(type, descriptor, qualifiers)
        }
    }
}
