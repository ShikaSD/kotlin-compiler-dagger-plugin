package me.shika.test.dagger

import me.shika.test.resolver.ResolverContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.constants.KClassValue

class DaggerComponentDescriptor(
    val definition: ClassDescriptor,
    val file: KtFile,
    val context: ResolverContext
) {
    val annotation = definition.componentAnnotation()!!
    val modules = annotation.value(context, "modules")
    val moduleInstances = modules.filter { it.isInstance() }
    val dependencies = annotation.value(context, "dependencies")
    val scopes = definition.scopeAnnotations()
}

private val DAGGER_COMPONENT_ANNOTATION = FqName("dagger.Component")

private fun ClassDescriptor.componentAnnotation() =
    if (modality == Modality.ABSTRACT && this !is AnnotationDescriptor) {
        annotations.findAnnotation(DAGGER_COMPONENT_ANNOTATION)
    } else {
        null
    }

private fun AnnotationDescriptor.value(context: ResolverContext, name: String) =
    (argumentValue(name)?.value as? List<KClassValue>)
        ?.mapNotNull {
            val value = it.getArgumentType(context.module)
            value.constructor.declarationDescriptor as? ClassDescriptor
        }
        ?: emptyList()

private fun ClassDescriptor.isInstance() =
    !DescriptorUtils.isObject(this) && modality != Modality.ABSTRACT

fun ClassDescriptor.isComponent() =
    annotations.hasAnnotation(DAGGER_COMPONENT_ANNOTATION)
