package me.shika.di.resolver.validation

import me.shika.di.model.Binding
import me.shika.di.resolver.ResolverContext
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor.Kind.Function
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor.Kind.KFunction
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor.Kind.KSuspendFunction
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor.Kind.SuspendFunction
import org.jetbrains.kotlin.builtins.getFunctionalClassKind
import org.jetbrains.kotlin.types.KotlinType

class ExtractFunctions : DiBindingProcessor {
    override fun ResolverContext.process(resolvedBindings: Sequence<Binding>): Sequence<Binding> =
        resolvedBindings.map {
            when (it) {
                is Binding.Instance -> it.functionBinding() ?: it
                else -> it
            }
        }

    private fun Binding.Instance.functionBinding(): Binding.Function? {
        val kind = type.constructor.declarationDescriptor?.getFunctionalClassKind() ?: return null

        return when (kind) {
            Function,
            KFunction -> type.extractFunctionBinding()
            SuspendFunction,
            KSuspendFunction -> TODO()
        }
    }

    private fun KotlinType.extractFunctionBinding(): Binding.Function {
        val from = arguments.dropLast(1).map { it.type }
        val to = arguments.last().type

        return Binding.Function(from, to)
    }
}
