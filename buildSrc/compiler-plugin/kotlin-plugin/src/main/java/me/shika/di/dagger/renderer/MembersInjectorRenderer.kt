package me.shika.di.dagger.renderer

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.dsl.function
import me.shika.di.dagger.renderer.dsl.markOverride
import me.shika.di.dagger.renderer.dsl.markPrivate
import me.shika.di.dagger.renderer.dsl.nestedClass
import me.shika.di.dagger.renderer.provider.ProviderSpec
import me.shika.di.dagger.renderer.provider.getValue
import me.shika.di.dagger.renderer.provider.initializeDeps
import me.shika.di.model.Endpoint
import me.shika.di.model.Injectable
import me.shika.di.model.ResolveResult

class MembersInjectorRenderer(
    private val componentBuilder: TypeSpec.Builder,
    private val componentName: ClassName,
    private val factoryRenderer: GraphRenderer
) {
    fun getMembersInjector(injectedType: TypeName, results: List<ResolveResult>): PropertySpec? {
        val membersInjectorType = injectedType.injector()

        return componentBuilder.propertySpecs.find { it.type == membersInjectorType }
            ?: componentBuilder.addMembersInjector(injectedType, results)
    }

    private fun TypeSpec.Builder.addMembersInjector(injectedTypeName: TypeName, results: List<ResolveResult>): PropertySpec {
        val renderedName = injectedTypeName.asString().capitalize()
        val injectorName = "${renderedName}_MembersInjector"
        val injectorTypeName = componentName.nestedClass(injectorName)
        val injectedParamName = renderedName.decapitalize()

        val injectedFactories = results.map { (it.endpoint as Endpoint.Injected).value }
            .zip(results.map { it.graph.map { factoryRenderer.getProvider(it) } })

        val factoryParams = injectedFactories.flatMap { it.second }.filterNotNull().distinct()

        nestedClass(injectorName) {
            markPrivate()
            addSuperinterface(injectedTypeName.injector())
            initializeDeps(factoryParams)
            function("injectMembers") {
                markOverride()
                addParameter(injectedParamName, injectedTypeName)
                injectedFactories.generateInjects(injectedParamName).forEach {
                    addCode(it)
                }
            }
        }

        val property = PropertySpec.builder(
            injectorTypeName.asFqString().decapitalize(),
            injectedTypeName.injector(),
            KModifier.PRIVATE
        ).initializer(
            injectorTypeName.let {
                val params = Array(factoryParams.size) { "%N" }.joinToString()
                CodeBlock.of("%T($params)", it, *factoryParams.map { it.property }.toTypedArray())
            }
        )
            .build()
        addProperty(property)

        return property
    }

    private fun List<Pair<Injectable, List<ProviderSpec?>>>.generateInjects(injectedParamName: String): List<CodeBlock> =
        map { (injectable, factories) ->
            when (injectable) {
                is Injectable.Setter -> {
                    val setter = injectable.descriptor.name.asString()
                    val params = factories.filterNotNull().joinToString { it.getValue() }
                    CodeBlock.of("$injectedParamName.$setter($params)\n")
                }
                is Injectable.Property -> {
                    val parameter = injectable.descriptor.name.asString()
                    val factory = factories.single()
                    val value = factory?.getValue()
                    CodeBlock.of("$injectedParamName.$parameter = $value\n")
                }
            }
        }

    private fun TypeName.injector() = with(ParameterizedTypeName.Companion) {
        ClassName("dagger", "MembersInjector")
            .parameterizedBy(this@injector)
    }
}
