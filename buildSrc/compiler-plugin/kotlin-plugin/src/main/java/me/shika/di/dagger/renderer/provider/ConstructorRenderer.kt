package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.asString
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.Constructor

class ConstructorRenderer(
    private val componentName: ClassName,
    private val deps: List<ProviderSpec>
) : ProviderRenderer<Constructor> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: Constructor): ProviderSpec {
        val constructedClass = variation.source.constructedClass
        val returnType = constructedClass.defaultType.typeName()!!
        val renderedName = returnType.asString()
        val providerName = "${renderedName}_Provider"

        val providerType = providerImpl(providerName, returnType, deps, providerBody(returnType, deps))

        return providerProperty(
            providerName.decapitalize(),
            deps,
            componentName.nestedClass(providerName),
            providerType,
            doubleCheck = binding.scopes.isNotEmpty()
        )
    }

    private fun providerBody(className: TypeName, deps: List<ProviderSpec>): CodeBlock {
        val params = deps.joinToString { it.getValue() }
        return CodeBlock.of("return %T($params)", className)
    }
}
