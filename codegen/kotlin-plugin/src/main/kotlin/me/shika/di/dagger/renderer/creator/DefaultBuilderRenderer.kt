package me.shika.di.dagger.renderer.creator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier.LATEINIT
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.asString
import me.shika.di.dagger.renderer.dsl.companionObject
import me.shika.di.dagger.renderer.dsl.function
import me.shika.di.dagger.renderer.dsl.nestedClass
import me.shika.di.dagger.renderer.parameterName
import me.shika.di.dagger.renderer.typeName
import me.shika.di.model.Key

class DefaultBuilderRenderer(
    private val componentClassName: ClassName,
    private val constructorParams: List<Key>,
    private val builder: TypeSpec.Builder
) {
    private val builderClassName = componentClassName.nestedClass(BUILDER_IMPL_NAME)

    fun render() {
        builder.apply {
            builderClass()
            builderPublicMethod()
        }
    }

    private fun TypeSpec.Builder.builderClass() {
        nestedClass(BUILDER_IMPL_NAME) {
            val paramToProperty = constructorParams.associateWith {
                PropertySpec.builder(it.parameterName(), it.type.typeName()!!, PRIVATE, LATEINIT)
                    .mutable(true)
                    .build()
            }
            addProperties(paramToProperty.values)
            paramToProperty.values.forEach {
                createMethod(it)
            }

            function("build") {
                val params = constructorParams.joinToString { paramToProperty[it]!!.name }
                addCode(
                    "return %T(${params})",
                    componentClassName
                )
            }
        }
    }

    private fun TypeSpec.Builder.createMethod(param: PropertySpec) {
        val name = param.type.asString().decapitalize()
        function(name) {
            returns(builderClassName)
            addParameter(ParameterSpec.builder(name, param.type).build())
            addCode("this.%N = ${name}\n", param)
            addCode("return this")
        }
    }

    private fun TypeSpec.Builder.builderPublicMethod() {
        companionObject {
            function("builder") {
                returns(builderClassName)
                addCode("return ${BUILDER_IMPL_NAME}()")
            }
        }
    }

    companion object {
        private const val BUILDER_IMPL_NAME = "Builder"
    }
}
