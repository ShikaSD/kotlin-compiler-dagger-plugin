package me.shika.test.dagger

import me.shika.test.AMBIGUOUS_BINDING
import me.shika.test.DaggerErrorMessages
import me.shika.test.model.Binding
import me.shika.test.model.Endpoint
import me.shika.test.model.GraphNode
import me.shika.test.model.ResolveResult
import me.shika.test.resolver.classDescriptor
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.reportFromPlugin
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker

class DaggerBindingResolver(
    val reporter: MessageCollector,
    val bindingDescriptor: DaggerBindingDescriptor
) {
    val component = bindingDescriptor.componentDescriptor
    val storage = component.context.resolveSession.storageManager
    val trace = component.context.trace

    val cachedNodeResolve = storage.createMemoizedFunction<Pair<KotlinType, DeclarationDescriptor>, GraphNode> { (type, source) ->
        type.resolveNode(source)
    }

    fun resolve(): List<ResolveResult> =
        bindingDescriptor.endpoints.map { it.resolveDependencies() }

    private fun Endpoint.resolveDependencies(): ResolveResult {
        val nodes = types.map { cachedNodeResolve(it!! to source) }
        return ResolveResult(this, nodes)
    }

    private fun KotlinType.resolveNode(source: DeclarationDescriptor): GraphNode {
        val canProvide = bindingDescriptor.bindings.filter {
            val returnType = it.type ?: return@filter false
            NewKotlinTypeChecker.equalTypes(returnType, this)
        } + listOfNotNull(injectableConstructor())

        if (canProvide.isEmpty()) {
            reporter.report(
                CompilerMessageSeverity.EXCEPTION,
                "Cannot provide $this"
            )
        }

        if (canProvide.size > 1) {
            trace.reportFromPlugin(
                AMBIGUOUS_BINDING.on(source.findPsi()!!),
                DaggerErrorMessages
            )
//            reporter.report(
//                CompilerMessageSeverity.ERROR,
//                "Ambiguous binding for $this: found $canProvide"
//            )
        }

        val binding = canProvide.first()
        val bindingDescriptor = binding.resolvedDescriptor

        val scopeAnnotations = binding.scopes.map { it.fqName }
        val componentScopeNames = component.scopes.map { it.fqName }
        if (!componentScopeNames.containsAll(scopeAnnotations)) {
            reporter.report(
                CompilerMessageSeverity.EXCEPTION,
                "Component ${component.definition} and ${bindingDescriptor?.name} scopes do not match: " +
                        "component - $componentScopeNames," +
                        " binding - $scopeAnnotations"
            )
        }

        return GraphNode(binding, binding.resolveDependencies())
    }

    private fun Binding.resolveDependencies(): List<GraphNode> =
        resolvedDescriptor?.valueParameters
            ?.map {
                cachedNodeResolve(it.type to resolvedDescriptor!!)
            } ?: emptyList()

    // TODO Remove from here?
    private fun KotlinType.injectableConstructor(): Binding.Constructor? {
        val classDescriptor = classDescriptor() ?: return null
        val injectableConstructors = classDescriptor.constructors.filter {
            it.annotations.hasAnnotation(INJECT_FQ_NAME) && it.typeParametersCount == 0 // TODO: type parameterized injections?
        }
        if (injectableConstructors.size > 1) {
            reporter.report(CompilerMessageSeverity.EXCEPTION, "Class can have only one @Inject annotated constructor, found $injectableConstructors")
        }
        return injectableConstructors.firstOrNull()?.let { Binding.Constructor(it, classDescriptor.scopeAnnotations()) }
    }
}
