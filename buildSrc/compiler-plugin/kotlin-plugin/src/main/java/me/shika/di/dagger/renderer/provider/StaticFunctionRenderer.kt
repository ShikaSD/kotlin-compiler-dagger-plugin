package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.StaticFunction
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

class StaticFunctionRenderer(
    private val componentName: ClassName,
    private val deps: List<ProviderSpec>
) : ProviderRenderer<StaticFunction> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: StaticFunction): ProviderSpec {
        val parent = variation.source.containingDeclaration as? ClassDescriptor
        val parentType = parent?.typeName()
        val renderedName = binding.renderedName(parentType)
        val returnType = variation.returnType()!!
        val providerName = "${renderedName}_Provider"

        val providerType = providerImpl(providerName, returnType, deps, providerBody(variation.source, parentType))

        return providerProperty(
            providerName.decapitalize(),
            deps,
            componentName.nestedClass(providerName),
            providerType,
            doubleCheck = binding.scopes.isNotEmpty()
        )
    }

    private fun providerBody(source: FunctionDescriptor, parentType: TypeName?): CodeBlock {
        val params = deps.joinToString(",") { it.getValue() }
        return CodeBlock.of(
            "return %T.${source.name}($params)",
            parentType
        )
    }

    private fun StaticFunction.returnType() =
        source.returnType?.typeName()
}
