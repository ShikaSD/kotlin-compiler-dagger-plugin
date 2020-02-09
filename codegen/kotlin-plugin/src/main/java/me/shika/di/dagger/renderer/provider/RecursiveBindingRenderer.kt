package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.asString
import me.shika.di.dagger.renderer.dsl.property
import me.shika.di.dagger.renderer.provider.ProviderSpec.ProviderType.Provider
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.Recursive

class RecursiveBindingRenderer : BindingRenderer<Recursive> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: Recursive): ProviderSpec {
        val typeName = binding.key.type.typeName()!!
        val propertyName = "recursive_${typeName.asString()}_Provider"

        val property = property(propertyName, providerOf(typeName)) {
            initializer("%T()", DELEGATE_NAME)
        }

        return ProviderSpec(property, Provider)
    }

    companion object {
        private val DELEGATE_NAME = ClassName("dagger.internal", "DelegateFactory")
    }
}
