package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.model.Binding

interface ProviderRenderer<in Variation : Binding.Variation> {
    fun TypeSpec.Builder.render(binding: Binding, variation: Variation): ProviderSpec
}

data class ProviderSpec(
    val property: PropertySpec,
    val type: ProviderType
) {
    sealed class ProviderType {
        object Provider : ProviderType()
        object Value : ProviderType()
    }
}
