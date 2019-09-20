package me.shika.di.resolver

import me.shika.di.model.Binding
import org.jetbrains.kotlin.types.KotlinType

data class ComponentDescriptor(
    val returnType: KotlinType,
    val bindings: List<Binding>
)
