package me.shika.test.dagger.resolver.creator

import me.shika.test.dagger.resolver.scopeAnnotations
import me.shika.test.model.Binding
import me.shika.test.model.Key
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor

interface CreatorDescriptor {
    val instances: List<Binding>
}

fun ValueParameterDescriptor.toInstanceBinding() =
    Binding(
        Key(type, scopeAnnotations()),
        emptyList(),
        Binding.Variation.BoundInstance(this)
    )
