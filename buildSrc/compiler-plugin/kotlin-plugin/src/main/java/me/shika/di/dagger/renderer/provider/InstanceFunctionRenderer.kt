package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.provider.Provider.ProviderType
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.InstanceFunction
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

class InstanceFunctionRenderer(
    private val componentName: ClassName,
    private val deps: List<Provider>
) : ProviderRenderer<InstanceFunction> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: InstanceFunction): Provider {
        val parent = variation.source.containingDeclaration as? ClassDescriptor
        val parentType = parent?.typeName()!!
        val renderedName = binding.renderedName(parentType)
        val returnType = variation.returnType()!!
        val providerName = "${renderedName}_Provider"

        val instanceProperty = instanceProperty(parentType)
        val depsWithParent = deps + Provider(instanceProperty, ProviderType.Value)

        val providerType = providerImpl(providerName, returnType, depsWithParent, instanceProperty.providerBody(variation.source))

        return providerProperty(
            providerName.decapitalize(),
            depsWithParent,
            componentName.nestedClass(providerName),
            providerType,
            isScoped = binding.scopes.isNotEmpty()
        )
    }

    private fun PropertySpec.providerBody(source: FunctionDescriptor): CodeBlock {
        val params = deps.joinToString(",") { it.getValue() }
        return CodeBlock.of(
            "return %N.${source.name}($params)",
            this
        )
    }

    private fun InstanceFunction.returnType() =
        source.returnType?.typeName()

    private fun TypeSpec.Builder.instanceProperty(type: TypeName): PropertySpec =
        propertySpecs.first { it.type == type }
}
