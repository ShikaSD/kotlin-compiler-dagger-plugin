package me.shika.test.dagger

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

private val SCOPE_FQ_NAME = FqName("javax.inject.Scope")
