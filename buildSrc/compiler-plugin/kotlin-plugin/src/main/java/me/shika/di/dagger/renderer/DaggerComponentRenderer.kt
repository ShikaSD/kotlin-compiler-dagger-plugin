package me.shika.di.dagger.renderer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import me.shika.di.dagger.resolver.DaggerComponentDescriptor
import me.shika.di.dagger.resolver.creator.DaggerFactoryDescriptor
import me.shika.di.model.Binding
import me.shika.di.model.Endpoint
import me.shika.di.model.ResolveResult
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import java.io.File

class DaggerComponentRenderer(
    private val componentDescriptor: DaggerComponentDescriptor
) {
    private val definition = componentDescriptor.definition
    private val componentClassName = ClassName(
        componentDescriptor.file.packageFqName.render(),
        componentDescriptor.nameString()
    )

    fun render(sourcesDir: File) {
        val fileSpec = FileSpec.builder(componentClassName.packageName, componentClassName.simpleName)
            .addType(renderComponent(componentDescriptor.graph))
            .build()

        fileSpec.writeTo(sourcesDir)
    }

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
        val method = (componentDescriptor.creatorDescriptor as? DaggerFactoryDescriptor)?.method ?: return@apply
        me.shika.di.dagger.renderer.creator.DaggerFactoryRenderer(componentClassName, this)
            .render(componentDescriptor.creatorDescriptor as DaggerFactoryDescriptor)
    }

    private fun TypeSpec.Builder.addDependencyInstances() = apply {
        val properties = componentDescriptor.creatorDescriptor?.instances
            ?.map { (it.bindingType as Binding.Variation.BoundInstance).source.type }
            ?.map {
                val name = it.typeName()?.name()!!.decapitalize()
                PropertySpec.builder(name, it.typeName()!!, PRIVATE)
                    .initializer(name)
                    .build()
            } ?: emptyList()
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
        val membersInjectorRenderer =
            DaggerMembersInjectorRenderer(this, componentClassName, factoryRenderer)
        results.groupBy { it.endpoint.source }
            .map { (_, results) ->
                val result = results.first()
                when (val endpoint = result.endpoint) {
                    is Endpoint.Provided -> {
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
