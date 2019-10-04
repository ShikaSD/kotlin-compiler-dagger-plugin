package me.shika.test.dagger

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import me.shika.test.model.Endpoint
import me.shika.test.model.ResolveResult
import me.shika.test.resolver.classDescriptor
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
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
            .apply {
                if (definition.visibility == Visibilities.INTERNAL) {
                    addModifiers(INTERNAL)
                }
            }
            .extendComponent()
            .createFactory()
            .addDependencyInstances()
            .addBindings(results)
            .build()

    private fun TypeSpec.Builder.extendComponent() = apply {
        if (definition.kind == ClassKind.INTERFACE) {
            addSuperinterface(definition.className())
        } else {
            superclass(definition.className())
        }
    }

    private fun TypeSpec.Builder.createFactory() = apply {
        addType(
            TypeSpec.classBuilder("Factory")
                .apply {
                    val factoryMethod = componentDescriptor.factoryDescriptor.factoryMethod
                    addSuperinterface(componentDescriptor.factoryDescriptor.factory.className())
                    addModifiers(PRIVATE)
                    addFunction(
                        FunSpec.builder(factoryMethod.name.asString())
                            .addModifiers(OVERRIDE)
                            .apply {
                                factoryMethod.valueParameters.forEach {
                                    addParameter(
                                        ParameterSpec.builder(it.name.asString(), it.type.typeName()!!)
                                            .build()
                                    )
                                }
                            }
                            .returns(componentClassName)
                            .addCode(
                                CodeBlock.of(
                                    "return ${componentDescriptor.nameString()}(${factoryMethod.valueParameters.joinToString { it.name.asString() }})"
                                )
                            )
                            .build()
                    )
                }
                .build()
        )

        addType(
            TypeSpec.companionObjectBuilder()
                .addFunction(
                    FunSpec.builder("factory")
                        .returns(componentDescriptor.factoryDescriptor.factory.className())
                        .addCode(
                            CodeBlock.of(
                                "return Factory()"
                            )
                        )
                        .build()
                )
                .build()
        )
    }

    private fun TypeSpec.Builder.addDependencyInstances() = apply {
        val properties = componentDescriptor.factoryDescriptor.factoryMethod.valueParameters.mapNotNull { it.type }.map {
            val name = it.typeName()?.name()!!.decapitalize()
            PropertySpec.builder(name, it.typeName()!!, PRIVATE)
                .initializer(name)
                .build()
        }
        val params = properties.map { it.toParameter() }

        primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameters(params)
                .build()
        )
        addProperties(properties)
    }

    private fun TypeSpec.Builder.addBindings(results: List<ResolveResult>) = apply {
        val factoryRenderer = DaggerFactoryRenderer(this, componentClassName)
        val membersInjectorRenderer = DaggerMembersInjectorRenderer(this, componentClassName, factoryRenderer)
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

fun KotlinType.typeName(): TypeName? = classDescriptor()?.fqNameSafe?.let {
    val types = arguments.map { it.type.typeName()!! }
    val typed = ClassName(it.parent().asString(), it.shortName().asString())
        .let {
            if (types.isNotEmpty()) {
                with(ParameterizedTypeName.Companion) {
                    it.parameterizedBy(*types.toTypedArray())
                }
            } else {
                it
            }
        }
    typed.copy(nullable = this.isMarkedNullable)
}

fun TypeName.string(): String =
    when (this) {
        is ClassName -> simpleName
        is ParameterizedTypeName -> rawType.simpleName + "_" + typeArguments.joinToString(separator = "_") { it.string() }
        is WildcardTypeName,
        is Dynamic,
        is LambdaTypeName,
        is TypeVariableName -> TODO()
    } + ("_nullable".takeIf { isNullable } ?: "")

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

internal fun TypeName.name() = string()

internal fun DaggerComponentDescriptor.nameString() =
    "Dagger${definition.name}"
