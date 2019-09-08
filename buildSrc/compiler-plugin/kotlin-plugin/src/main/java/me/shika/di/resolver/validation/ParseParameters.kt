package me.shika.di.resolver.validation

import me.shika.di.model.Binding
import me.shika.di.resolver.ResolverContext

class ParseParameters : DiBindingProcessor {
    override fun ResolverContext.process(resolvedBindings: Sequence<Binding>): Sequence<Binding> {
        val arguments = resolvedCall.valueArguments
        if (arguments.size != 1) {
            return emptySequence()
        }

        val varargArgument = arguments.values.first()
        val argumentTypes = varargArgument.arguments.map { trace.getType(it.getArgumentExpression()!!)!! }
        return resolvedBindings + argumentTypes.map { Binding.Instance(it) }
    }
}
