package me.shika.di.dagger.renderer.provider

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.asString
import me.shika.di.dagger.renderer.dsl.function
import me.shika.di.dagger.renderer.dsl.markOverride
import me.shika.di.dagger.renderer.dsl.markPrivate
import me.shika.di.dagger.renderer.dsl.nestedClass
import me.shika.di.dagger.renderer.dsl.property
import me.shika.di.dagger.renderer.letIf
import me.shika.di.dagger.renderer.provider.Provider.ProviderType
import me.shika.di.dagger.renderer.toParameter
import me.shika.di.model.Binding

internal fun TypeSpec.Builder.initializeDeps(factories: List<Provider>) {
    val properties = factories.map {
        PropertySpec.builder(it.property.name, it.property.type, *it.property.modifiers.toTypedArray())
            .initializer(it.property.name)
            .build()
    }

    // Inner static class to generate binding
    addProperties(properties)
    primaryConstructor(
        FunSpec.constructorBuilder()
            .addParameters(properties.map { it.toParameter() })
            .build()
    )
}

internal fun Provider.getValue() = when(type) {
    ProviderType.Provider -> "${property.name}.get()"
    ProviderType.Value -> property.name
}

internal fun providerOf(typeName: TypeName) =
    with(ParameterizedTypeName.Companion) {
        PROVIDER_CLASS_NAME.parameterizedBy(typeName)
    }

internal fun TypeSpec.Builder.providerImpl(
    providerName: String,
    returnType: TypeName,
    dependencies: List<Provider>,
    providerBody: CodeBlock
): TypeName {
    val providerTypeName = providerOf(returnType)

    nestedClass(providerName) {
        markPrivate()
        addSuperinterface(providerTypeName)
        initializeDeps(dependencies)

        function("get") {
            markOverride()
            returns(returnType)
            addCode(providerBody)
        }
    }

    return providerTypeName
}



internal fun TypeSpec.Builder.providerProperty(
    propertyName: String,
    deps: List<Provider>,
    constructorType: TypeName,
    propertyType: TypeName,
    isScoped: Boolean
) = Provider(
    property = property(propertyName, propertyType) {
        markPrivate()
        initializeProvider(constructorType, deps.map { it.property.name }, isScoped)
    },
    type = ProviderType.Provider
)

internal fun PropertySpec.Builder.initializeProvider(
    providerType: TypeName,
    params: List<String>,
    isScoped: Boolean
): PropertySpec.Builder = apply {
    val args = hashMapOf(
        "doubleCheck" to DOUBLE_CHECK_NAME,
        "factoryType" to providerType
    )

    initializer(
        CodeBlock.builder()
            .addNamed(
                "%factoryType:T(${params.joinToString()})".letIf(isScoped) {
                    "%doubleCheck:M($it)"
                },
                args
            )
            .build()
    )
}

internal fun Binding.renderedName(parentType: TypeName?): String {
    val qualifiers = key.qualifiers.joinToString(
        separator = "_",
        prefix = "_"
    ) { it.fqName?.shortName()?.asString()?.decapitalize().orEmpty() }
    val methodName = bindingType.source.name.asString().capitalize()
    return "${parentType?.asString()}_$methodName$qualifiers"
}

private val PROVIDER_CLASS_NAME = ClassName("javax.inject", "Provider")
private val DOUBLE_CHECK_NAME = MemberName(ClassName("dagger.internal", "DoubleCheck"), "provider")
