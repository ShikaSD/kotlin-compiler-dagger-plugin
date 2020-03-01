package me.shika.di.dagger.renderer.dsl

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

fun TypeSpec.Builder.property(name: String, type: TypeName, block: PropertySpec.Builder.() -> Unit) =
    PropertySpec.builder(name, type).apply(block).build().also {
        addProperty(it)
    }

fun PropertySpec.Builder.markPrivate() = addModifiers(KModifier.PRIVATE)
