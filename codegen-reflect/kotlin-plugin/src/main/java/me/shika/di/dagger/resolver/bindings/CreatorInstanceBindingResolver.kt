package me.shika.di.dagger.resolver.bindings

import me.shika.di.dagger.resolver.creator.CreatorDescriptor
import me.shika.di.model.Binding

class CreatorInstanceBindingResolver(
    private val creatorDescriptor: CreatorDescriptor
): BindingResolver {
    override fun invoke(): List<Binding> =
        creatorDescriptor.instances
}
