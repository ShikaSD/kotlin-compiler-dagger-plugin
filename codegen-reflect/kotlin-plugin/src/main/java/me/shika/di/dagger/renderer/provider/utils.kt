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
import me.shika.di.dagger.renderer.provider.ProviderSpec.ProviderType
import me.shika.di.dagger.renderer.toParameter
import me.shika.di.model.Binding

internal fun TypeSpec.Builder.initializeDeps(factories: List<ProviderSpec>) {
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

internal fun ProviderSpec.getValue() = when(type) {
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
    dependencies: List<ProviderSpec>,
    providerBody: CodeBlock
): TypeName {
    val providerTypeName = providerOf(returnType)

    nestedClass(providerName) {
        markPrivate()
        addSuperinterface(providerTypeName)
        initializeDeps(dependencies.distinct())

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
    deps: List<ProviderSpec>,
    constructorType: TypeName,
    propertyType: TypeName,
    doubleCheck: Boolean
) = ProviderSpec(
    property = property(propertyName, propertyType) {
        markPrivate()
        initializeProvider(constructorType, deps.distinct().map { it.property.name }, doubleCheck)
    },
    type = ProviderType.Provider
)

internal fun PropertySpec.Builder.initializeProvider(
    providerType: TypeName,
    params: List<String>,
    doubleCheck: Boolean
): PropertySpec.Builder = apply {
    val args = hashMapOf(
        "doubleCheck" to DOUBLE_CHECK_PROVIDER,
        "factoryType" to providerType
    )

    initializer(
        CodeBlock.builder()
            .addNamed(
                "%factoryType:T(${params.joinToString()})".letIf(doubleCheck) {
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
internal val DOUBLE_CHECK_NAME = ClassName("dagger.internal", "DoubleCheck")
private val DOUBLE_CHECK_PROVIDER = MemberName(DOUBLE_CHECK_NAME, "provider")
