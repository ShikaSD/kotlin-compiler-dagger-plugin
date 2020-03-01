package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
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
        val parentType = parent.property.type
        val currentType = binding.key.type.typeName()
        val cast = castIfNeeded(parentType, currentType)

        val initCodeBlock = when (parent.type) {
            ProviderType.Provider -> CodeBlock.of("%M(${parent.property.name})$cast", DOUBLE_CHECK_LAZY)
            ProviderType.Value -> {
                val providerName = binding.renderedName(componentName)
                val providerType = providerImpl(
                    providerName = providerName,
                    returnType = variation.innerType.typeName()!!,
                    dependencies = deps,
                    providerBody = CodeBlock.of("return %N", deps.first().property)
                )
                CodeBlock.of("%M(%T(${parent.property.name}))$cast", DOUBLE_CHECK_LAZY, providerType)
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

    private fun castIfNeeded(parentType: TypeName, currentType: TypeName?) =
        if (parentType is ParameterizedTypeName && currentType is ParameterizedTypeName) {
            val parentInnerType = parentType.typeArguments.first()
            val currentInnerType = currentType.typeArguments.first()
            if (parentInnerType != currentInnerType) "as Lazy<$currentInnerType>" else ""
        } else { "" }

    companion object {
        private val DOUBLE_CHECK_LAZY = MemberName(DOUBLE_CHECK_NAME, "lazy")
    }
}
