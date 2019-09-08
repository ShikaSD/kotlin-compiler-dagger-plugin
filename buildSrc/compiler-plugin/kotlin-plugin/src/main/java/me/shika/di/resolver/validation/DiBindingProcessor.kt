package me.shika.di.resolver.validation

import me.shika.di.model.Binding
import me.shika.di.resolver.ResolverContext

interface DiBindingProcessor {
    fun ResolverContext.process(resolvedBindings: Sequence<Binding>): Sequence<Binding>
}
