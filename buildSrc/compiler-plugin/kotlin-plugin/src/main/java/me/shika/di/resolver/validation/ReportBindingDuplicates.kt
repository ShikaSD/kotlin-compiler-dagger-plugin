package me.shika.di.resolver.validation

import me.shika.di.model.Binding
import me.shika.di.resolver.ResolverContext

class ReportBindingDuplicates : DiBindingProcessor {
    override fun ResolverContext.process(resolvedBindings: Sequence<Binding>): Sequence<Binding> =
        resolvedBindings.also { checkDuplicates(it) }

    private fun ResolverContext.checkDuplicates(bindings: Sequence<Binding>) {
        bindings
            .groupBy { it.type }
            .filter { it.value.size > 1 }
            .forEach {
                // trace.reportFromPlugin()
                TODO("report duplicates")
            }
    }
}
