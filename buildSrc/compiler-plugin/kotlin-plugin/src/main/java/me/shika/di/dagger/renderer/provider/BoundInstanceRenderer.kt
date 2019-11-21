package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.provider.Provider.ProviderType.Value
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.BoundInstance

class BoundInstanceRenderer() : ProviderRenderer<BoundInstance> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: BoundInstance): Provider {
        val instanceType = variation.source.type
        val returnType = instanceType.typeName()!!

        val property = propertySpecs.first { it.type == returnType }
        return Provider(property, Value)
    }
}
