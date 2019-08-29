package me.shika.test.dagger

import com.squareup.kotlinpoet.*
import me.shika.test.model.Endpoint
import me.shika.test.model.Injectable
import me.shika.test.model.ResolveResult

class DaggerMembersInjectorRenderer(
    private val componentBuilder: TypeSpec.Builder,
    private val componentName: ClassName,
    private val factoryRenderer: DaggerFactoryRenderer
) {
    fun getMembersInjector(injectedType: TypeName, results: List<ResolveResult>): PropertySpec? {
        val membersInjectorType = injectedType.injector()

        return componentBuilder.propertySpecs.find { it.type == membersInjectorType }
            ?: componentBuilder.addMembersInjector(injectedType, results)
    }

    private fun TypeSpec.Builder.addMembersInjector(injectedTypeName: TypeName, results: List<ResolveResult>): PropertySpec {
        val injectorTypeName = componentName.nestedClass("${injectedTypeName.name()}_MembersInjector")
        val injectedParamName = injectedTypeName.name().decapitalize()

        val injectedFactories = results.map { (it.endpoint as Endpoint.Injected).value }
            .zip(results.map { it.graph.map { factoryRenderer.getFactory(it) } })

        val factoryParams = injectedFactories.flatMap { it.second }.filterNotNull().distinct()

        val type = classWithFactories(factoryParams, injectorTypeName, injectedTypeName.injector())
            .addFunction(
                FunSpec.builder("injectMembers")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(injectedParamName, injectedTypeName)
                    .apply {
                        injectedFactories.generateInjects(injectedParamName).forEach {
                            addCode(it)
                        }
                    }
                    .build()
            )
            .build()
        addType(type)

        val property = PropertySpec.builder(
            injectorTypeName.name().decapitalize(),
            injectedTypeName.injector(),
            KModifier.PRIVATE
        ).initializer(
            injectorTypeName.let {
                val params = Array(factoryParams.size) { "%N" }.joinToString()
                CodeBlock.of("%T($params)", it, *factoryParams.toTypedArray())
            }
        )
            .build()
        addProperty(property)

        return property
    }

    private fun List<Pair<Injectable, List<PropertySpec?>>>.generateInjects(injectedParamName: String): List<CodeBlock> =
        map { (injectable, factories) ->
            when (injectable) {
                is Injectable.Setter -> {
                    val setter = injectable.descriptor.name.asString()
                    val params = factories.joinToString { "${it?.name}.get()" }
                    CodeBlock.of("$injectedParamName.$setter($params)\n")
                }
                is Injectable.Property -> {
                    val parameter = injectable.descriptor.name.asString()
                    val factory = factories.single()
                    val value = "${factory?.name}.get()"
                    CodeBlock.of("$injectedParamName.$parameter = $value\n")
                }
            }
        }

    private fun TypeName.injector() = with(ParameterizedTypeName.Companion) {
        ClassName("dagger", "MembersInjector")
            .parameterizedBy(this@injector)
    }
}
