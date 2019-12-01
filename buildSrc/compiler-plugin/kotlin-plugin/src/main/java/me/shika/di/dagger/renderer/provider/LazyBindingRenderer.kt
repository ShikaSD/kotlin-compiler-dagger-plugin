package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.dsl.markPrivate
import me.shika.di.dagger.renderer.dsl.property
import me.shika.di.dagger.renderer.provider.ProviderSpec.ProviderType
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.Lazy

class LazyBindingRenderer(
    private val componentName: ClassName,
    private val deps: List<ProviderSpec>
) : BindingRenderer<Lazy> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: Lazy): ProviderSpec {
        val parent = deps.first()
        val initCodeBlock = when (parent.type) {
            ProviderType.Provider -> doubleCheckLazy(parent.property.name)
            ProviderType.Value -> {
                val providerName = binding.renderedName(componentName)
                val providerType = providerImpl(
                    providerName = providerName,
                    returnType = variation.innerType.typeName()!!,
                    dependencies = deps,
                    providerBody = CodeBlock.of("return %N", deps.first().property)
                )
                doubleCheckLazy("%T(${parent.property.name})", providerType)
            }
        }

        val lazyProperty = property(
            "${parent.property.name}_Lazy",
            binding.key.type.typeName()!!
        ) {
            markPrivate()
            initializer(initCodeBlock)
        }

        return ProviderSpec(
            lazyProperty,
            ProviderType.Value
        )
    }
}
