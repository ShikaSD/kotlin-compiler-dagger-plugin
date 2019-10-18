package me.shika.di.dagger.renderer.dsl

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import me.shika.di.dagger.renderer.typeName
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

fun TypeSpec.Builder.nestedClass(name: String, block: TypeSpec.Builder.() -> Unit) = addType(
    TypeSpec.classBuilder(name).apply(block).build()
)

fun TypeSpec.Builder.companionObject(name: String? = null, block: TypeSpec.Builder.() -> Unit) = addType(
    TypeSpec.companionObjectBuilder(name).apply(block).build()
)

fun TypeSpec.Builder.markPrivate() = addModifiers(KModifier.PRIVATE)

fun TypeSpec.Builder.function(name: String, block: FunSpec.Builder.() -> Unit) = addFunction(
    FunSpec.builder(name).apply(block).build()
)

fun TypeSpec.Builder.primaryConstructor(block: FunSpec.Builder.() -> Unit) = primaryConstructor(
    FunSpec.constructorBuilder().apply(block).build()
)

fun TypeSpec.Builder.overrideFunction(descriptor: FunctionDescriptor, block: FunSpec.Builder.() -> Unit) =
    function(descriptor.name.asString()) {
        addModifiers(KModifier.OVERRIDE)
        parametersFrom(descriptor)
        returns(descriptor.returnType?.typeName()!!)
        block()
    }
