package me.shika.di.dagger.resolver.creator

import me.shika.di.dagger.resolver.qualifiers
import me.shika.di.model.Binding
import me.shika.di.model.Key
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor

interface CreatorDescriptor {
    val instances: List<Binding>
}

fun ValueParameterDescriptor.toInstanceBinding() =
    Binding(
        Key(type, qualifiers()),
        emptyList(),
        Binding.Variation.BoundInstance(this)
    )
