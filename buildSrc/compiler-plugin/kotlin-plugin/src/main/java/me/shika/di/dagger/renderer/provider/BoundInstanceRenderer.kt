package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.provider.ProviderSpec.ProviderType.Value
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.BoundInstance

class BoundInstanceRenderer() : BindingRenderer<BoundInstance> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: BoundInstance): ProviderSpec {
        val instanceType = variation.source.type
        val returnType = instanceType.typeName()!!

        val property = propertySpecs.first { it.type == returnType }
        return ProviderSpec(property, Value)
    }
}