package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.asString
import me.shika.di.dagger.renderer.dsl.markPrivate
import me.shika.di.dagger.renderer.dsl.property
import me.shika.di.dagger.renderer.provider.ProviderSpec.ProviderType.Value
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.Component

class ComponentBindingRenderer(
    private val componentName: TypeName
): BindingRenderer<Component> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: Component): ProviderSpec =
        ProviderSpec(
            property = property("component_${componentName.asString()}", componentName) {
                markPrivate()
                initializer("this")
            },
            type = Value
        )

}
