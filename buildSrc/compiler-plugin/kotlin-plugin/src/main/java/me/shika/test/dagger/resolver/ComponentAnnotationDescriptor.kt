package me.shika.test.dagger.resolver

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor

class ComponentAnnotationDescriptor(
    val context: ResolverContext,
    val annotation: AnnotationDescriptor
) {
    val dependencies: List<ClassDescriptor> = annotation.classListValue(context, DAGGER_COMPONENT_DEPS_PARAM)
    val modules: List<ClassDescriptor> = annotation.classListValue(context, DAGGER_COMPONENT_MODULES_PARAM)

    val moduleInstances by lazy { modules.filter { it.isInstance() } }
    val dependencyComponents by lazy { dependencies.filter { it.isComponent() } }

}
const val DAGGER_COMPONENT_MODULES_PARAM = "modules"
const val DAGGER_COMPONENT_DEPS_PARAM = "dependencies"
