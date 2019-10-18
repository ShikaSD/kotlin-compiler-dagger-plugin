package me.shika.di.dagger.resolver.creator

import me.shika.di.BUILDER_DEPENDENCIES_NOT_PROVIDED
import me.shika.di.BUILDER_MODULE_NOT_PROVIDED
import me.shika.di.BUILDER_SETTER_TOO_MANY_PARAMS
import me.shika.di.BUILDER_SETTER_WRONG_RETURN_TYPE
import me.shika.di.BUILDER_WRONG_BUILD_METHOD
import me.shika.di.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.di.dagger.resolver.DAGGER_BINDS_INSTANCE_FQ_NAME
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.allDescriptors
import me.shika.di.dagger.resolver.report
import me.shika.di.model.Binding
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class DaggerBuilderDescriptor(
    private val component: ClassDescriptor,
    private val definition: ClassDescriptor,
    private val componentAnnotation: ComponentAnnotationDescriptor,
    private val context: ResolverContext
) : CreatorDescriptor {
    var buildMethod: FunctionDescriptor? = null
        private set

    var setters: List<FunctionDescriptor> = emptyList()
        private set

    override var instances: List<Binding> = emptyList()
        private set

    init {
        parseDefinition()
    }

    private fun parseDefinition() {
        val methods = definition.allDescriptors(DescriptorKindFilter.FUNCTIONS)
            .filterIsInstance<FunctionDescriptor>()
            .filter { it.modality == Modality.ABSTRACT }

        val buildMethods = methods.filter {
            it.valueParameters.size == 0 && component.defaultType.isSubtypeOf(it.returnType!!)
        }

        if (buildMethods.size != 1) {
            definition.report(context.trace) { BUILDER_WRONG_BUILD_METHOD.on(it, buildMethods) }
            return
        }
        buildMethod = buildMethods.firstOrNull()

        val setterMethods = methods - buildMethods
        if (!validateSetters(setterMethods)) return
        validateDependencies(setterMethods)
        setters = setterMethods

        instances = setterMethods.filter { it.annotations.hasAnnotation(DAGGER_BINDS_INSTANCE_FQ_NAME) }
            .map { it.valueParameters.first().toInstanceBinding() }
    }

    private fun validateSetters(setters: List<FunctionDescriptor>): Boolean {
        var isValid = true
        setters.forEach { setter ->
            if (setter.valueParameters.size != 1) {
                setter.report(context.trace) { BUILDER_SETTER_TOO_MANY_PARAMS.on(it) }
                isValid = false
            }

            if (!KotlinBuiltIns.isUnit(setter.returnType!!) && !definition.defaultType.isSubtypeOf(setter.returnType!!)) {
                setter.report(context.trace) { BUILDER_SETTER_WRONG_RETURN_TYPE.on(it) }
                isValid = false
            }
        }

        return isValid
    }

    private fun validateDependencies(setters: List<FunctionDescriptor>) {
        val declaredDependencies = componentAnnotation.dependencies
        val declaredModuleInstances = componentAnnotation.moduleInstances

        val notSetDependency = declaredDependencies.filterNot { dep ->
            setters.any { it.paramType() == dep.defaultType }
        }
        val notSetModule = declaredModuleInstances.filterNot { module ->
            setters.any { it.paramType() == module.defaultType }
        }

        notSetDependency.forEach { dep -> definition.report(context.trace) { BUILDER_DEPENDENCIES_NOT_PROVIDED.on(it, dep.defaultType) } }
        notSetModule.forEach { module -> definition.report(context.trace) { BUILDER_MODULE_NOT_PROVIDED.on(it, module.defaultType) } }
    }
}

private fun FunctionDescriptor.paramType() = valueParameters.first().type
