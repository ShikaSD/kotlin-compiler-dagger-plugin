package me.shika.test.dagger

import com.squareup.kotlinpoet.*
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
            .addBindings(results)
            .build()

    private fun TypeSpec.Builder.extendComponent() = apply {
        if (definition.kind == ClassKind.INTERFACE) {
            addSuperinterface(definition.className())
        } else {
            superclass(definition.className())
        }
    }

    private fun TypeSpec.Builder.addBindings(results: List<ResolveResult>) = apply {
        results.groupBy { it.endpoint.source }
            .map { (_, list) ->
                val endpoint = list.first().endpoint
                when (endpoint) {
                    is Endpoint.Exposed -> {
                        val factory = addFactory(componentClassName, endpoint.source)
                        addFunction(endpoint.source, override = true) {
                            addCode("return ${factory?.simpleName}.get()")
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

private fun KotlinType.typeName(): TypeName? =
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
                if (override) addModifiers(KModifier.OVERRIDE)
                signature.valueParameters.forEach {
                    addParameter(it.name.asString(), it.type.typeName()!!)
                }
                returns(signature.returnType!!.typeName()!!)
            }
            .apply(builder)
            .build()
    )
}

private fun TypeSpec.Builder.addFactory(componentName: ClassName, signature: FunctionDescriptor): MemberName? {
    val returnType = signature.returnType?.typeName() ?: return null
    val name = "${signature.containingDeclaration.name}_${signature.name.asString().capitalize()}" // FIXME replace with module provider
    val factoryName = componentName.nestedClass("${name}_Factory")
    val factoryMemberName = MemberName(componentName, factoryName.simpleName.decapitalize())
    addType(
        TypeSpec.classBuilder(factoryName)
            .addSuperinterface(returnType.provider())
            .addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(returnType)
                    .build()
            )
            .build()
    )
    addProperty(
        PropertySpec.builder(factoryMemberName.simpleName, factoryName)
            .initializer(CodeBlock.of("%T()", factoryName))
            .build()
    )
    return factoryMemberName
}

private fun TypeName.provider() =
    with (ParameterizedTypeName.Companion) {
        ClassName("javax.inject", "Provider")
            .parameterizedBy(this@provider)
    }

private fun TypeName.name() =
    when (this) {
        is ClassName -> simpleName
        is ParameterizedTypeName -> rawType.simpleName
        else -> ""
    }

private fun DaggerComponentDescriptor.nameString() =
    "Dagger${definition.name}"
