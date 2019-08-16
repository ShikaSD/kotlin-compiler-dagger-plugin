package me.shika.test.dagger

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import me.shika.test.model.Binding
import me.shika.test.model.Endpoint
import me.shika.test.model.GraphNode
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
                        val factory = addFactory(componentClassName, list.first())
                        addFunction(endpoint.source, override = true) {
                            addCode("return $factory.get()")
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

private fun TypeSpec.Builder.addFactory(componentName: ClassName, result: ResolveResult): String? =
    addFactoryIfNeeded(componentName, result.bindings)?.name

private fun TypeSpec.Builder.addFactoryIfNeeded(componentName: ClassName, graphNode: GraphNode): PropertySpec? {
    val signature = graphNode.value.resolvedDescriptor
    val returnType = signature.returnType?.typeName() ?: return null // FIXME report
    val typeProvider = returnType.provider()

    val parent = signature.containingDeclaration as ClassDescriptor
    val parentType = if (parent.isCompanionObject) {
        (parent.containingDeclaration as ClassDescriptor).defaultType.typeName()
    } else {
        parent.defaultType.typeName()
    }

    val name = when (graphNode.value) {
        is Binding.StaticFunction,
        is Binding.InstanceFunction -> "${parentType?.name()}_${signature.name.asString().capitalize()}" // FIXME replace with module provider
        is Binding.Constructor -> "${graphNode.value.descriptor.constructedClass.name}"
    }

    val factoryType = componentName.nestedClass("${name}_Factory")
    val factoryMemberName = "${name}_Provider".decapitalize()

    return this.propertySpecs.find { it.type == typeProvider }.ifNull {
        val depsFactories = graphNode.dependencies.mapNotNull { addFactoryIfNeeded(componentName, it) }

        addType(
            TypeSpec.classBuilder(factoryType)
                .addSuperinterface(returnType.provider())
                .addProperties(
                    depsFactories.map {
                        PropertySpec.builder(it.name, it.type, PRIVATE)
                            .initializer(it.name)
                            .build()
                    }
                )
                .apply {
                    if (graphNode.value is Binding.InstanceFunction) {
                        val moduleType = graphNode.value.moduleInstance.defaultType.typeName()
                        addProperty(
                            PropertySpec.builder(moduleType!!.name().decapitalize(), moduleType, PRIVATE)
                                .initializer(moduleType.name().decapitalize())
                                .build()
                        )
                    }
                }
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(
                            depsFactories.map {
                                ParameterSpec.builder(it.name, it.type)
                                    .build()
                            }
                        )
                        .apply {
                            if (graphNode.value is Binding.InstanceFunction) {
                                val moduleType = graphNode.value.moduleInstance.defaultType.typeName()
                                addParameter(moduleType!!.name().decapitalize(), moduleType)
                            }
                        }
                        .build()
                )
                .addFunction(
                    FunSpec.builder("get")
                        .addModifiers(OVERRIDE)
                        .returns(returnType)
                        .addCode(
                            when (graphNode.value) {
                                is Binding.StaticFunction -> {
                                    CodeBlock.of(
                                        "return %T.${signature.name}(${depsFactories.joinToString(",") { "${it.name}.get()" }})",
                                        parentType
                                    )
                                }
                                is Binding.Constructor -> {
                                    CodeBlock.of(
                                        "return %T(${depsFactories.joinToString(",") { "${it.name}.get()" }})",
                                        parentType
                                    )
                                }
                                else -> CodeBlock.of("")
                            }
                        )
                        .build()
                )
                .build()
        )

        val spec = PropertySpec.builder(factoryMemberName, typeProvider)
            .initializer(
                CodeBlock.of(
                    "%T(${depsFactories.map { it.name }.joinToString(",")})".let {
                        if (graphNode.value.scopes.isNotEmpty()) {
                            "dagger.internal.DoubleCheck.provider($it)"
                        } else {
                            it
                        }
                    },
                    factoryType
                )
            )
            .build()

        addProperty(spec)
        spec
    }
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

private fun <T : Any> T?.ifNull(action: () -> T): T =
    if (this == null) { action() } else this
