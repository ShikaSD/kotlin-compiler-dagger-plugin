package me.shika.di.resolver.validation

import me.shika.di.model.Binding
import me.shika.di.resolver.ResolverContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.typeUtil.immediateSupertypes

class ExtractAnonymousTypes : DiBindingProcessor {
    override fun ResolverContext.process(resolvedBindings: Sequence<Binding>): Sequence<Binding> =
        resolvedBindings.map {
            when (it) {
                is Binding.Instance -> it.resolveAnonymousBindings()
                else -> it
            }
        }

    private fun Binding.Instance.resolveAnonymousBindings(): Binding.Instance =
        if (DescriptorUtils.isAnonymousObject(type.constructor.declarationDescriptor!!)) {
            copy(type = type.immediateSupertypes().single())
        } else {
            this
        }
}
