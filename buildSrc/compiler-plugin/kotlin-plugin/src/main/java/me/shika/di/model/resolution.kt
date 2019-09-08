package me.shika.di.model

import org.jetbrains.kotlin.types.KotlinType

data class GraphNode(val value: Binding, val dependencies: List<GraphNode>)

sealed class Binding {
    abstract val type: KotlinType

    data class Instance(override val type: KotlinType): Binding()
    data class Function(val from: List<KotlinType>, override val type: KotlinType): Binding()
    data class Constructor(val from: List<KotlinType>, override val type: KotlinType): Binding()
}
