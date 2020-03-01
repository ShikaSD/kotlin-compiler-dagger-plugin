package me.shika.di.dagger.resolver

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName

class ComponentAnnotationDescriptor(
    val context: ResolverContext,
    val annotation: AnnotationDescriptor
) {
    val dependencies: List<ClassDescriptor> = annotation.classListValue(context, DAGGER_COMPONENT_DEPS_PARAM)
    val modules: List<ClassDescriptor> = annotation.classListValue(context, DAGGER_COMPONENT_MODULES_PARAM)
        .flatMap { it.resolveChildModules() + it }

    val moduleInstances by lazy { modules.filter { it.isInstance() } }
    val dependencyComponents by lazy { dependencies.filter { it.isComponent() } }

    private fun ClassDescriptor.resolveChildModules(): List<ClassDescriptor> =
        annotations.findAnnotation(DAGGER_MODULE_FQ_NAME)
            ?.classListValue(context, DAGGER_MODULE_INCLUDES_PARAM)
            .orEmpty()
            .flatMap {
                it.resolveChildModules() + it
            }
}

private const val DAGGER_COMPONENT_MODULES_PARAM = "modules"
private const val DAGGER_COMPONENT_DEPS_PARAM = "dependencies"
private const val DAGGER_MODULE_INCLUDES_PARAM = "includes"

private val DAGGER_MODULE_FQ_NAME = FqName("dagger.Module")
