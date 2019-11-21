package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.Equality

class EqualityProviderRenderer(
    private val deps: List<Provider>
) : ProviderRenderer<Equality> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: Equality): Provider =
        deps.first()
}
