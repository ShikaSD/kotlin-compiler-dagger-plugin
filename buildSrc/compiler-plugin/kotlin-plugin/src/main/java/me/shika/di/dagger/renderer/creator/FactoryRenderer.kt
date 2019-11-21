package me.shika.di.dagger.renderer.creator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.dsl.companionObject
import me.shika.di.dagger.renderer.dsl.function
import me.shika.di.dagger.renderer.dsl.markPrivate
import me.shika.di.dagger.renderer.dsl.nestedClass
import me.shika.di.dagger.renderer.dsl.overrideFunction
import me.shika.di.dagger.renderer.typeName
import me.shika.di.dagger.resolver.creator.DaggerFactoryDescriptor
import me.shika.di.model.Key
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

class FactoryRenderer(
    private val componentClassName: ClassName,
    private val constructorParams: List<Key>,
    private val builder: TypeSpec.Builder
) {
    fun render(factoryDescriptor: DaggerFactoryDescriptor) {
        val method = factoryDescriptor.method ?: return
        val factoryClass = factoryDescriptor.method?.containingDeclaration as? ClassDescriptor ?: return
        val factoryInterfaceName = factoryClass.typeName() ?: return

        builder.apply {
            factoryClass(factoryInterfaceName, method, isInterface = factoryClass.kind == ClassKind.INTERFACE)
            factoryPublicMethod(factoryInterfaceName)
        }
    }

    private fun TypeSpec.Builder.factoryClass(
        factoryClassName: TypeName,
        factoryMethod: FunctionDescriptor,
        isInterface: Boolean
    ) {
        nestedClass(FACTORY_IMPL_NAME) {
            markPrivate()
            if (isInterface) {
                addSuperinterface(factoryClassName)
            } else {
                superclass(factoryClassName)
            }
            overrideFunction(factoryMethod) {
                addCode(
                    "return %T(${factoryMethod.params()})",
                    componentClassName
                )
            }
        }
    }

    private fun TypeSpec.Builder.factoryPublicMethod(factoryClassName: TypeName) {
        companionObject {
            function("factory") {
                returns(factoryClassName)
                addCode("return $FACTORY_IMPL_NAME()")
            }
        }
    }

    private fun FunctionDescriptor.params(): String =
        constructorParams.mapNotNull { paramKey ->
            valueParameters.find { param ->
                param.type == paramKey.type && paramKey.qualifiers.all { param.annotations.hasAnnotation(it.fqName!!) }
            }
        }.joinToString { it.name.asString() }

    companion object {
        private const val FACTORY_IMPL_NAME = "Factory"
    }
}
