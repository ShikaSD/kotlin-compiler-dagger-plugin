package me.shika.di.dagger.renderer.creator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.typeName
import me.shika.di.dagger.resolver.creator.DaggerFactoryDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

class DaggerFactoryRenderer(
    private val componentClassName: ClassName,
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
        addType(
            TypeSpec.classBuilder(FACTORY_IMPL_NAME).apply {
                addModifiers(KModifier.PRIVATE)
                if (isInterface) {
                    addSuperinterface(factoryClassName)
                } else {
                    superclass(factoryClassName)
                }
                addFunction(
                    FunSpec.builder(factoryMethod.name.asString())
                        .addModifiers(KModifier.OVERRIDE)
                        .apply {
                            factoryMethod.valueParameters.forEach {
                                addParameter(
                                    ParameterSpec.builder(it.name.asString(), it.type.typeName()!!)
                                        .build()
                                )
                            }
                        }
                        .returns(factoryMethod.returnType?.typeName()!!)
                        .addCode(
                            CodeBlock.of(
                                "return %T(${factoryMethod.valueParameters.joinToString { it.name.asString() }})",
                                componentClassName
                            )
                        )
                        .build()
                )
            }
                .build()
        )
    }

    private fun TypeSpec.Builder.factoryPublicMethod(factoryClassName: TypeName) {
        addType(
            TypeSpec.companionObjectBuilder()
                .addFunction(
                    FunSpec.builder("factory")
                        .addAnnotation(ClassName("", "JvmStatic"))
                        .returns(factoryClassName)
                        .addCode(
                            CodeBlock.of(
                                "return $FACTORY_IMPL_NAME()"
                            )
                        )
                        .build()
                )
                .build()
        )
    }

    companion object {
        private const val FACTORY_IMPL_NAME = "Factory"
    }
}
