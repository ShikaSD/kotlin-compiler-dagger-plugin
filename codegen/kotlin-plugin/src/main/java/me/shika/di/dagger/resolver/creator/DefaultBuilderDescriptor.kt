package me.shika.di.dagger.resolver.creator

import me.shika.di.model.Binding

class DefaultBuilderDescriptor() : CreatorDescriptor {
    override val instances: List<Binding> = emptyList()
}
