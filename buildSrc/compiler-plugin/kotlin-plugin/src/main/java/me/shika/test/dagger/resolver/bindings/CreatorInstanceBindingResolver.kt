package me.shika.test.dagger.resolver.bindings

import me.shika.test.dagger.resolver.creator.CreatorDescriptor
import me.shika.test.model.Binding

class CreatorInstanceBindingResolver(
    private val creatorDescriptor: CreatorDescriptor
): BindingResolver {
    override fun invoke(): List<Binding> =
        creatorDescriptor.instances
}
