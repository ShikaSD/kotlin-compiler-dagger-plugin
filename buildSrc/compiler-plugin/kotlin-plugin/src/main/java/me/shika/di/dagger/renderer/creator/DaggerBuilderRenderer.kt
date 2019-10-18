package me.shika.di.dagger.renderer.creator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier.LATEINIT
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.dsl.companionObject
import me.shika.di.dagger.renderer.dsl.function
import me.shika.di.dagger.renderer.dsl.nestedClass
import me.shika.di.dagger.renderer.dsl.overrideFunction
import me.shika.di.dagger.renderer.parameterName
import me.shika.di.dagger.renderer.typeName
import me.shika.di.dagger.resolver.QUALIFIER_FQ_NAME
import me.shika.di.dagger.resolver.creator.DaggerBuilderDescriptor
import me.shika.di.model.Key
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

class DaggerBuilderRenderer(
    private val componentClassName: ClassName,
    private val constructorParams: List<Key>,
    private val builder: TypeSpec.Builder
) {
    fun render(builderDescriptor: DaggerBuilderDescriptor) {
        val builderClass = builderDescriptor.buildMethod?.containingDeclaration as? ClassDescriptor ?: return
        val builderClassName = builderClass.typeName()!!
        builder.apply {
            builderClass(
                builderClassName,
                builderDescriptor.setters,
                builderDescriptor.buildMethod,
                isInterface = builderClass.kind == ClassKind.INTERFACE
            )
            builderPublicMethod(builderClassName)
        }
    }

    private fun TypeSpec.Builder.builderClass(
        builderClassName: TypeName,
        setters: List<FunctionDescriptor>,
        buildMethod: FunctionDescriptor?,
        isInterface: Boolean
    ) {
        nestedClass(BUILDER_IMPL_NAME) {
            addModifiers(PRIVATE)
            if (isInterface) {
                addSuperinterface(builderClassName)
            } else {
                superclass(builderClassName)
            }

            val paramToProperty = constructorParams.associateWith {
                PropertySpec.builder(it.parameterName(), it.type.typeName()!!, PRIVATE, LATEINIT)
                    .mutable(true)
                    .build()
            }
            addProperties(paramToProperty.values)

            setters.forEach {
                overrideFunction(it) {
                    val param = it.valueParameters.first()
                    val qualifiers = it.annotations.filter { it.type.annotations.hasAnnotation(QUALIFIER_FQ_NAME) }
                    val property = paramToProperty[Key(param.type, qualifiers)]
                    addCode("%N = ${param.name.asString()}\n", property)

                    if (!KotlinBuiltIns.isUnit(it.returnType!!)) {
                        addCode("return this")
                    }
                }
            }

            overrideFunction(buildMethod!!) {
                val params = constructorParams.joinToString { paramToProperty[it]!!.name }
                addCode(
                    "return %T(${params})",
                    componentClassName
                )
            }
        }
    }

    private fun TypeSpec.Builder.builderPublicMethod(builderClassName: TypeName) {
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