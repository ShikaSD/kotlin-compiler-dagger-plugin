package me.shika.test.dagger

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import me.shika.test.model.Endpoint
import me.shika.test.model.ResolveResult
import me.shika.test.resolver.classDescriptor
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

class DaggerComponentRenderer(
    val componentDescriptor: DaggerComponentDescriptor,
    val messageCollector: MessageCollector
) {
    val definition = componentDescriptor.definition
    val componentClassName = ClassName(
        componentDescriptor.file.packageFqName.render(),
        componentDescriptor.nameString()
    )

    fun render(results: List<ResolveResult>): FileSpec =
        FileSpec.builder(componentClassName.packageName, componentClassName.simpleName)
            .addType(renderComponent(results))
            .build()

    private fun renderComponent(results: List<ResolveResult>): TypeSpec =
        TypeSpec.classBuilder(componentDescriptor.nameString())
            .extendComponent()
            .addModuleInstances()
            .addBindings(results)
            .build()

    private fun TypeSpec.Builder.extendComponent() = apply {
        if (definition.kind == ClassKind.INTERFACE) {
            addSuperinterface(definition.className())
        } else {
            superclass(definition.className())
        }
    }

    private fun TypeSpec.Builder.addModuleInstances() = apply {
        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameters(
                    componentDescriptor.moduleInstances.map {
                        ParameterSpec.builder(it.className().simpleName.decapitalize(), it.className())
                            .build()
                    }
                )
                .build()
        )
        addProperties(
            componentDescriptor.moduleInstances.map {
                PropertySpec.builder(it.className().simpleName.decapitalize(), it.className(), PRIVATE)
                    .initializer(it.className().simpleName.decapitalize())
                    .build()
            }
        )
    }

    private fun TypeSpec.Builder.addBindings(results: List<ResolveResult>) = apply {
        val factoryRenderer = DaggerFactoryRenderer(this, componentClassName)
        results.groupBy { it.endpoint.source }
            .map { (_, list) ->
                val endpoint = list.first().endpoint
                when (endpoint) {
                    is Endpoint.Exposed -> {
                        val factory = factoryRenderer.getFactory(list.first().graph)
                        addFunction(endpoint.source, override = true) {
                            addCode("return %N.get()", factory)
                        }
                    }
                    is Endpoint.Injected -> {
                        addFunction(endpoint.source, override = true) {

                        }
                    }
                }
            }
    }
}

private fun ClassDescriptor.className() =
    ClassName(
        packageName = fqNameSafe.parentOrNull()?.takeIf { it != FqName.ROOT }?.asString().orEmpty(),
        simpleName = name.asString()
    )

internal fun KotlinType.typeName(): TypeName? =
    classDescriptor()?.className()?.let {
        with(ParameterizedTypeName.Companion) {
            val type = if (arguments.isNotEmpty()) {
                val arguments = arguments.mapNotNull { it.type.typeName() }
                    .toTypedArray()
                it.parameterizedBy(*arguments)
            } else {
                it
            }
            type.copy(nullable = isMarkedNullable)
        }
    }

private fun TypeSpec.Builder.addFunction(signature: FunctionDescriptor, override: Boolean = false, builder: FunSpec.Builder.() -> Unit) {
    addFunction(
        FunSpec.builder(signature.name.asString())
            .apply {
                if (override) addModifiers(OVERRIDE)
                signature.valueParameters.forEach {
                    addParameter(it.name.asString(), it.type.typeName()!!)
                }
                returns(signature.returnType!!.typeName()!!)
            }
            .apply(builder)
            .build()
    )
}

internal fun TypeName.name() =
    when (this) {
        is ClassName -> simpleName
        is ParameterizedTypeName -> rawType.simpleName
        else -> ""
    }

internal fun DaggerComponentDescriptor.nameString() =
    "Dagger${definition.name}"
