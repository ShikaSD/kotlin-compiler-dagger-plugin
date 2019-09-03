package me.shika.di.dagger

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.model.Endpoint
import me.shika.di.model.ResolveResult
import me.shika.di.resolver.classDescriptor
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
    private val componentDescriptor: DaggerComponentDescriptor,
    private val messageCollector: MessageCollector
) {
    private val definition = componentDescriptor.definition
    private val componentClassName = ClassName(
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
        val properties = (componentDescriptor.moduleInstances + componentDescriptor.dependencies).map {
            val name = it.className().simpleName.decapitalize()
            PropertySpec.builder(name, it.className(), PRIVATE)
                .initializer(name)
                .build()
        }
        val params = properties.map { it.toParameter() }

        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameters(params)
                .build()
        )
        addProperties(properties)
    }

    private fun TypeSpec.Builder.addBindings(results: List<ResolveResult>) = apply {
        val factoryRenderer = DaggerFactoryRenderer(this, componentClassName)
        val membersInjectorRenderer =
            DaggerMembersInjectorRenderer(this, componentClassName, factoryRenderer)
        results.groupBy { it.endpoint.source }
            .map { (_, results) ->
                val result = results.first()
                when (val endpoint = result.endpoint) {
                    is Endpoint.Exposed -> {
                        val factory = factoryRenderer.getFactory(result.graph.single())
                        addFunction(endpoint.source, override = true) {
                            addCode("return %N.get()", factory)
                        }
                    }
                    is Endpoint.Injected -> {
                        addFunction(endpoint.source, override = true) {
                            val injectedValue = parameters.first()
                            val membersInjector = membersInjectorRenderer.getMembersInjector(injectedValue.type, results)
                            addCode("%N.injectMembers(${injectedValue.name})", membersInjector)
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
                val arguments = arguments.mapNotNull { it.type.typeName() }.toTypedArray()
                it.parameterizedBy(*arguments)
            } else {
                it
            }
            type.copy(nullable = isMarkedNullable)
        }
    }

private fun TypeSpec.Builder.addFunction(
    signature: FunctionDescriptor,
    override: Boolean = false,
    builder: FunSpec.Builder.() -> Unit
) {
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
