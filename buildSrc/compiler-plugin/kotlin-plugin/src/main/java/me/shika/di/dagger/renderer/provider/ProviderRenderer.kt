package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.model.Binding

interface ProviderRenderer<in Variation : Binding.Variation> {
    fun TypeSpec.Builder.render(binding: Binding, variation: Variation): Provider
}

class Provider(
    val property: PropertySpec,
    val type: ProviderType
) {
    sealed class ProviderType {
        object Provider : ProviderType()
        object Value : ProviderType()
    }
}
