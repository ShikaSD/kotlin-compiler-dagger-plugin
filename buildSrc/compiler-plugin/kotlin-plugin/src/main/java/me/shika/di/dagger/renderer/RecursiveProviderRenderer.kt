package me.shika.di.dagger.renderer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.provider.BoundInstanceRenderer
import me.shika.di.dagger.renderer.provider.ComponentProviderRenderer
import me.shika.di.dagger.renderer.provider.ConstructorRenderer
import me.shika.di.dagger.renderer.provider.EqualityProviderRenderer
import me.shika.di.dagger.renderer.provider.InstanceFunctionRenderer
import me.shika.di.dagger.renderer.provider.InstancePropertyRenderer
import me.shika.di.dagger.renderer.provider.Provider
import me.shika.di.dagger.renderer.provider.ProviderRenderer
import me.shika.di.dagger.renderer.provider.StaticFunctionRenderer
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.BoundInstance
import me.shika.di.model.Binding.Variation.Component
import me.shika.di.model.Binding.Variation.Constructor
import me.shika.di.model.Binding.Variation.Equality
import me.shika.di.model.Binding.Variation.InstanceFunction
import me.shika.di.model.Binding.Variation.InstanceProperty
import me.shika.di.model.Binding.Variation.StaticFunction
import me.shika.di.model.GraphNode

class RecursiveProviderRenderer(private val componentBuilder: TypeSpec.Builder, private val componentName: ClassName) {
    private val bindingToProp = mutableMapOf<Binding, Provider>()

    fun getProvider(graphNode: GraphNode): Provider? {
        return bindingToProp.getOrPut(graphNode.value) {
            componentBuilder.addFactory(graphNode)
        }
    }

    private fun TypeSpec.Builder.addFactory(graphNode: GraphNode): Provider {
        val deps = graphNode.dependencies.mapNotNull { getProvider(it) }
        return when (graphNode.value.bindingType) {
            is Constructor -> render(ConstructorRenderer(componentName, deps), graphNode)
            is InstanceFunction -> render(InstanceFunctionRenderer(componentName, deps), graphNode)
            is InstanceProperty -> render(InstancePropertyRenderer(componentName), graphNode)
            is StaticFunction -> render(StaticFunctionRenderer(componentName, deps), graphNode)
            is Equality -> render(EqualityProviderRenderer(deps), graphNode)
            is BoundInstance -> render(BoundInstanceRenderer(), graphNode)
            is Component -> render(ComponentProviderRenderer(componentName), graphNode)
        }
    }

    private fun <T : Binding.Variation> TypeSpec.Builder.render(renderer: ProviderRenderer<T>, graphNode: GraphNode) =
        renderer.run { render(graphNode.value, graphNode.value.bindingType as T) }
}
