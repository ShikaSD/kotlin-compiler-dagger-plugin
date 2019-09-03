package me.shika.di.dagger

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass

internal fun DeclarationDescriptor.scopeAnnotations(): List<AnnotationDescriptor> =
    annotations.filter {
        it.annotationClass?.annotations?.hasAnnotation(SCOPE_FQ_NAME) == true
    }

internal fun <T> T?.ifNull(action: () -> T?): T? =
    this ?: action()

internal fun <T> T.letIf(condition: Boolean, block: (T) -> T) =
    if (condition) {
        block(this)
    } else {
        this
    }

internal fun PropertySpec.toParameter() =
    ParameterSpec.builder(name, type, *modifiers.toTypedArray())
        .build()

fun classWithFactories(
    factories: List<PropertySpec>,
    type: ClassName,
    superInterface: TypeName
): TypeSpec.Builder {
    val properties = factories.map {
        PropertySpec.builder(it.name, it.type, *it.modifiers.toTypedArray())
            .initializer(it.name)
            .build()
    }

    // Inner static class to generate binding
    return TypeSpec.classBuilder(type)
        .addSuperinterface(superInterface)
        .addProperties(properties)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameters(properties.map { it.toParameter() })
                .build()
        )
}

private val SCOPE_FQ_NAME = FqName("javax.inject.Scope")
