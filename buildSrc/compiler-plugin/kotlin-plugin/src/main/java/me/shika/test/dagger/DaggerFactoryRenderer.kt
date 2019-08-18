package me.shika.test.dagger

import com.squareup.kotlinpoet.*
import me.shika.test.model.Binding
import me.shika.test.model.GraphNode
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.utils.addIfNotNull

class DaggerFactoryRenderer(private val componentBuilder: TypeSpec.Builder, private val componentName: ClassName) {
    fun getFactory(graphNode: GraphNode): PropertySpec? {
        val signature = graphNode.value.resolvedDescriptor
        val returnType = signature.returnType?.typeName() ?: return null // FIXME report
        val providerType = returnType.provider()

        return componentBuilder.propertySpecs.find { it.type == providerType }.ifNull {
            componentBuilder.addFactory(graphNode, signature, returnType, providerType)
        }
    }

    private fun TypeSpec.Builder.addFactory(
        graphNode: GraphNode,
        signature: FunctionDescriptor,
        returnType: TypeName,
        providerType: TypeName
    ): PropertySpec? {
        val parent = signature.containingDeclaration as ClassDescriptor
        val parentType = parent.type()
        val name = graphNode.bindingName(parentType)

        val factoryType = componentName.nestedClass("${name}_Factory")
        val factoryMemberName = "${name}_Provider".decapitalize()

        val depsFactories = graphNode.dependencies.mapNotNull { getFactory(it) }
        val properties = depsFactories.toMutableList().apply {
            addIfNotNull((graphNode.value as? Binding.InstanceFunction)?.toProperty())
        }.map {
            PropertySpec.builder(it.name, it.type, *it.modifiers.toTypedArray())
                .initializer(it.name)
                .build()
        }

        // Inner static class to generate binding
        addType(
            TypeSpec.classBuilder(factoryType)
                .addSuperinterface(providerType)
                .addProperties(properties)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(properties.map { it.toParameter() })
                        .build()
                )
                .addFunction(
                    FunSpec.builder("get")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(returnType)
                        .addCode(graphNode.providerBody(signature, depsFactories, parentType))
                        .build()
                )
                .build()
        )

        // Factory property in component (cached if scoped)
        val property = PropertySpec.builder(factoryMemberName, providerType)
            .factoryProperty(factoryType, depsFactories, parentType, graphNode.value.scopes.isNotEmpty())
            .build()

        addProperty(property)
        return property
    }

    private fun GraphNode.providerBody(
        signature: FunctionDescriptor,
        depsFactories: List<PropertySpec>,
        parentType: TypeName?
    ) = when (value) {
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
            is Binding.InstanceFunction -> {
                CodeBlock.of(
                    "return %N.${signature.name}(${depsFactories.joinToString(",") { "${it.name}.get()" }})",
                    value.moduleInstance.defaultType.typeName()?.name()?.decapitalize()
                )
            }
        }

    private fun PropertySpec.Builder.factoryProperty(
        factoryType: TypeName,
        depsFactories: List<PropertySpec>,
        parentType: TypeName?,
        isScoped: Boolean
    ) = apply {
        val params = depsFactories.map { it.name }.toMutableList()
        componentBuilder.propertySpecs.find { it.type == parentType }?.let { params += it.name }

        val doubleCheckName = MemberName(
            ClassName("dagger.internal", "DoubleCheck"),
            "provider"
        )

        val args = hashMapOf(
            "doubleCheck" to doubleCheckName,
            "factoryType" to factoryType
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

    private fun TypeName.provider() =
        with(ParameterizedTypeName.Companion) {
            ClassName("javax.inject", "Provider")
                .parameterizedBy(this@provider)
        }

    private fun PropertySpec.toParameter() =
        ParameterSpec.builder(name, type, *modifiers.toTypedArray())
            .build()

    private fun Binding.InstanceFunction.toProperty(): PropertySpec {
        val moduleType = moduleInstance.defaultType.typeName()
        return PropertySpec.builder(moduleType!!.name().decapitalize(), moduleType, KModifier.PRIVATE)
            .initializer(moduleType.name().decapitalize())
            .build()
    }

    private fun ClassDescriptor.type() = if (isCompanionObject) {
        (containingDeclaration as ClassDescriptor).defaultType.typeName()
    } else {
        defaultType.typeName()
    }

    private fun GraphNode.bindingName(parentType: TypeName?) = when (value) {
        is Binding.StaticFunction,
        is Binding.InstanceFunction -> "${parentType?.name()}_${value.resolvedDescriptor.name.asString().capitalize()}"
        is Binding.Constructor -> "${value.descriptor.constructedClass.name}"
    }
}
