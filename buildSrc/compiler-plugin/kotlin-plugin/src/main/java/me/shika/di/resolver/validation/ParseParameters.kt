package me.shika.di.resolver.validation

import me.shika.di.model.Binding
import me.shika.di.resolver.ResolverContext
import me.shika.di.resolver.varargArguments

class ParseParameters : DiBindingProcessor {
    override fun ResolverContext.process(resolvedBindings: Sequence<Binding>): Sequence<Binding> {
        val arguments = resolvedCall.valueArguments
        if (arguments.size != 1) {
            TODO()
        }

        val argumentTypes = resolvedCall.varargArguments().map { trace.getType(it.getArgumentExpression()!!)!! }
        return resolvedBindings + argumentTypes.map { Binding.Instance(it) }
    }
}
