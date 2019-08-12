package me.shika.test.dagger

import me.shika.test.model.Binding
import me.shika.test.model.Endpoint
import me.shika.test.model.ResolveResult
import me.shika.test.resolver.classDescriptor
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.types.KotlinType
import java.util.*

class DaggerBindingResolver(
    val reporter: MessageCollector,
    val bindingDescriptor: DaggerBindingDescriptor
) {
    fun resolve(): List<ResolveResult> =
        bindingDescriptor.endpoints.map { it.resolveDependencies() }

    private fun Endpoint.resolveDependencies(): ResolveResult {
        val component = bindingDescriptor.componentDescriptor

        val deque = ArrayDeque<KotlinType>()
        val result = mutableSetOf<Binding>()

        deque.push(type!!)

        while (deque.isNotEmpty()) {
            val element = deque.pop()
            val canProvide = bindingDescriptor.bindings.filter {
                val returnType = it.type ?: return@filter false
                returnType.constructor == element.constructor && returnType.arguments == element.arguments
            } + listOfNotNull(element.injectableConstructor())

            if (canProvide.isEmpty()) {
                reporter.report(
                    CompilerMessageSeverity.EXCEPTION,
                    "Cannot provide $element"
                )
            }

            if (canProvide.size > 1) {
                reporter.report(
                    CompilerMessageSeverity.EXCEPTION,
                    "Ambiguous binding for $element: found $canProvide"
                )
            }

            val binding = canProvide.first()
            val bindingDescriptor = binding.resolvedDescriptor

            val scopeAnnotations = binding.scopes.map { it.fqName }
            val componentScopeNames = component.scopes.map { it.fqName }
            if (!componentScopeNames.containsAll(scopeAnnotations)) {
                reporter.report(
                    CompilerMessageSeverity.EXCEPTION,
                    "Component ${component.definition} and ${bindingDescriptor.name} scopes do not match: " +
                            "component - $componentScopeNames," +
                            " binding - $scopeAnnotations"
                )
            }

            bindingDescriptor.valueParameters.forEach {
                deque.push(it.type)
            }

            result.add(binding)
        }

        return ResolveResult(this, result)
    }

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
