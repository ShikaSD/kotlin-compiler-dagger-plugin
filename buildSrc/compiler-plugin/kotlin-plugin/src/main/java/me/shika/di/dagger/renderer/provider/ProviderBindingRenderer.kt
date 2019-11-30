package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.provider.ProviderSpec.ProviderType
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.Provider

class ProviderBindingRenderer(
    private val componentName: ClassName,
    private val deps: List<ProviderSpec>
) : ProviderRenderer<Provider> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: Provider): ProviderSpec {
        val parent = deps.first()
        return when (parent.type) {
            ProviderType.Provider -> parent.copy(type = ProviderType.Value)
            ProviderType.Value -> {
                val providerName = binding.renderedName(componentName)
                val providerType = providerImpl(
                    providerName = providerName,
                    returnType = variation.innerType.typeName()!!,
                    dependencies = deps,
                    providerBody = CodeBlock.of("return %N", deps.first().property)
                )
                providerProperty(
                    providerName.decapitalize(),
                    deps,
                    componentName.nestedClass(providerName),
                    providerType,
                    doubleCheck = false
                ).copy(type = ProviderType.Value)
            }
        }
    }
}
