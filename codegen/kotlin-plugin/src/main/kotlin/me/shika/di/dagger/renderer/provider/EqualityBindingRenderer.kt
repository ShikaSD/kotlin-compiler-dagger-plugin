package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.Equality

class EqualityBindingRenderer(
    private val deps: List<ProviderSpec>
) : BindingRenderer<Equality> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: Equality): ProviderSpec =
        deps.first()
}
