package me.shika.di.dagger.renderer.dsl

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

fun TypeSpec.Builder.nestedClass(name: String, block: TypeSpec.Builder.() -> Unit) = addType(
    TypeSpec.classBuilder(name).apply(block).build()
)

fun TypeSpec.Builder.nestedInterface(name: String, block: TypeSpec.Builder.() -> Unit) = addType(
    TypeSpec.interfaceBuilder(name).apply(block).build()
)

fun TypeSpec.Builder.companionObject(name: String? = null, block: TypeSpec.Builder.() -> Unit) = addType(
    TypeSpec.companionObjectBuilder(name).apply(block).build()
)

fun TypeSpec.Builder.markPrivate() = addModifiers(KModifier.PRIVATE)

