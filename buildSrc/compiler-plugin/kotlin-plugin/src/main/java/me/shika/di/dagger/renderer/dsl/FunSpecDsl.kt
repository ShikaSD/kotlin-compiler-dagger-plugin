package me.shika.di.dagger.renderer.dsl

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import me.shika.di.dagger.renderer.typeName
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

fun FunSpec.Builder.parametersFrom(descriptor: FunctionDescriptor) = apply {
    descriptor.valueParameters.forEach {
        addParameter(
            ParameterSpec.builder(it.name.asString(), it.type.typeName()!!)
                .build()
        )
    }
}
