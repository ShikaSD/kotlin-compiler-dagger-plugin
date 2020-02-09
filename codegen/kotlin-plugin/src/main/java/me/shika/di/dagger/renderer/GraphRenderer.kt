package me.shika.di.dagger.renderer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.provider.BindingRenderer
import me.shika.di.dagger.renderer.provider.BoundInstanceBindingRenderer
import me.shika.di.dagger.renderer.provider.ComponentBindingRenderer
import me.shika.di.dagger.renderer.provider.ConstructorBindingRenderer
import me.shika.di.dagger.renderer.provider.EqualityBindingRenderer
import me.shika.di.dagger.renderer.provider.InstanceFunctionRenderer
import me.shika.di.dagger.renderer.provider.InstancePropertyRenderer
import me.shika.di.dagger.renderer.provider.LazyBindingRenderer
import me.shika.di.dagger.renderer.provider.ProviderBindingRenderer
import me.shika.di.dagger.renderer.provider.ProviderSpec
import me.shika.di.dagger.renderer.provider.RecursiveBindingRenderer
import me.shika.di.dagger.renderer.provider.StaticFunctionRenderer
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.BoundInstance
import me.shika.di.model.Binding.Variation.Component
import me.shika.di.model.Binding.Variation.Constructor
import me.shika.di.model.Binding.Variation.Equality
import me.shika.di.model.Binding.Variation.InstanceFunction
import me.shika.di.model.Binding.Variation.InstanceProperty
import me.shika.di.model.Binding.Variation.Lazy
import me.shika.di.model.Binding.Variation.Provider
import me.shika.di.model.Binding.Variation.Recursive
import me.shika.di.model.Binding.Variation.StaticFunction
import me.shika.di.model.GraphNode

class GraphRenderer(private val componentBuilder: TypeSpec.Builder, private val componentName: ClassName) {
    private val bindingToProp = mutableMapOf<Binding, ProviderSpec>()
    private val recursionResolved = mutableMapOf<Binding, Boolean>()

    fun getProvider(graphNode: GraphNode): ProviderSpec? {
        return getUnresolvedProvider(graphNode).also {
            componentBuilder.resolveRecursiveBindings()
        }
    }

    private fun getUnresolvedProvider(graphNode: GraphNode): ProviderSpec? {
        return bindingToProp.getOrPut(graphNode.value) {
            componentBuilder.addFactory(graphNode)
        }
    }

    private fun TypeSpec.Builder.addFactory(graphNode: GraphNode): ProviderSpec {
        val deps by lazy { graphNode.dependencies.mapNotNull { getUnresolvedProvider(it) } }
        return when (graphNode.value.bindingType) {
            is Constructor -> render(ConstructorBindingRenderer(componentName, deps), graphNode)
            is InstanceFunction -> render(InstanceFunctionRenderer(componentName, deps), graphNode)
            is InstanceProperty -> render(InstancePropertyRenderer(componentName), graphNode)
            is StaticFunction -> render(StaticFunctionRenderer(componentName, deps), graphNode)
            is Equality -> render(EqualityBindingRenderer(deps), graphNode)
            is BoundInstance -> render(BoundInstanceBindingRenderer(), graphNode)
            is Component -> render(ComponentBindingRenderer(componentName), graphNode)
            is Provider -> render(ProviderBindingRenderer(componentName, deps), graphNode)
            is Lazy -> render(LazyBindingRenderer(componentName, deps), graphNode)
            is Recursive -> render(RecursiveBindingRenderer(), graphNode)
        }
    }

    private fun TypeSpec.Builder.resolveRecursiveBindings() {
        val recursiveBindings = bindingToProp.keys.filter { it.bindingType is Recursive && recursionResolved[it] == null }
        recursiveBindings.forEach {
            val providerSpec = bindingToProp[it]
            val delegateSpec = bindingToProp[(it.bindingType as Recursive).delegate]
            addInitializerBlock(
                CodeBlock.of("%M(%N, %N)", DELEGATE_METHOD, providerSpec?.property, delegateSpec?.property)
            )
            recursionResolved[it] = true
        }
    }

    private fun <T : Binding.Variation> TypeSpec.Builder.render(renderer: BindingRenderer<T>, graphNode: GraphNode) =
        renderer.run { render(graphNode.value, graphNode.value.bindingType as T) }

    companion object {
        private val DELEGATE_METHOD = MemberName(ClassName("dagger.internal", "DelegateFactory"), "setDelegate")
    }
}
