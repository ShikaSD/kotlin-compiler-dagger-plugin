package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.provider.ProviderSpec.ProviderType
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Binding
import me.shika.di.model.Binding.Variation.InstanceProperty
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor

class InstancePropertyRenderer(private val componentName: ClassName) : ProviderRenderer<InstanceProperty> {
    override fun TypeSpec.Builder.render(binding: Binding, variation: InstanceProperty): ProviderSpec {
        val parent = variation.source.containingDeclaration as? ClassDescriptor
        val parentType = parent?.typeName()!!
        val renderedName = binding.renderedName(parentType)
        val returnType = variation.returnType()!!
        val providerName = "${renderedName}_Provider"

        val instanceProperty = instanceProperty(parentType)
        val parentDependency = listOf(ProviderSpec(instanceProperty, ProviderType.Value))

        val providerType = providerImpl(
            providerName,
            returnType,
            parentDependency,
            instanceProperty.providerBody(variation.source, parentType)
        )

        return providerProperty(
            providerName.decapitalize(),
            parentDependency,
            componentName.nestedClass(providerName),
            providerType,
            doubleCheck = binding.scopes.isNotEmpty()
        )
    }

    private fun PropertySpec.providerBody(source: PropertyDescriptor, parentType: TypeName?): CodeBlock {
        return CodeBlock.of(
            "return %N.${source.name}",
            this
        )
    }

    private fun InstanceProperty.returnType() =
        source.returnType?.typeName()

    private fun TypeSpec.Builder.instanceProperty(type: TypeName): PropertySpec =
        propertySpecs.first { it.type == type }
}
