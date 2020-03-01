package me.shika.di.dagger.resolver.bindings

import me.shika.di.BINDS_METHOD_NOT_ABSTRACT
import me.shika.di.BINDS_METHOD_NOT_ONE_PARAMETER
import me.shika.di.BINDS_TYPE_IS_NOT_ASSIGNABLE
import me.shika.di.MODULE_WITHOUT_ANNOTATION
import me.shika.di.PROVIDES_METHOD_ABSTRACT
import me.shika.di.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.allDescriptors
import me.shika.di.dagger.resolver.qualifiers
import me.shika.di.dagger.resolver.report
import me.shika.di.dagger.resolver.scopeAnnotations
import me.shika.di.model.Binding
import me.shika.di.model.Key
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class ModuleBindingResolver(
    private val componentAnnotation: ComponentAnnotationDescriptor,
    private val definition: ClassDescriptor,
    private val context: ResolverContext
): BindingResolver {
    override fun invoke(): List<Binding> =
        componentAnnotation.modules
            .flatMap { resolveBindings(it).toList() }

    private fun resolveBindings(module: ClassDescriptor): Sequence<Binding> {
        module.validateModuleAnnotation()

        val isInstance = module in componentAnnotation.moduleInstances

        val companionFunctions = module.companionObjectDescriptor
            ?.allDescriptors(DescriptorKindFilter.FUNCTIONS)
            ?: emptyList()

        val instanceFunctions = module.allDescriptors(DescriptorKindFilter.FUNCTIONS)

        val moduleFunctions = (instanceFunctions + companionFunctions).asSequence()
            .filterIsInstance<FunctionDescriptor>()

        return moduleFunctions.provisionBindings(isInstance) + moduleFunctions.typeEqualityBindings()
    }

    private fun ClassDescriptor.validateModuleAnnotation() {
        if (!annotations.hasAnnotation(DAGGER_MODULE_FQ_NAME)) {
            report(context.trace) { MODULE_WITHOUT_ANNOTATION.on(it, definition) }
        }
    }

    private fun Sequence<FunctionDescriptor>.provisionBindings(isInstance: Boolean) =
        filter { it.annotations.hasAnnotation(PROVIDES_FQ_NAME) }
            .map {
                it.validateProvisionBinding()
                Binding(
                    Key(it.returnType!!, it.qualifiers()),
                    it.scopeAnnotations(),
                    when {
                        isInstance -> Binding.Variation.InstanceFunction(it)
                        else -> Binding.Variation.StaticFunction(it)
                    }
                )
            }

    private fun FunctionDescriptor.validateProvisionBinding() {
        if (modality == Modality.ABSTRACT) {
            report(context.trace) { PROVIDES_METHOD_ABSTRACT.on(it) }
        }
    }

    private fun Sequence<FunctionDescriptor>.typeEqualityBindings() =
        filter { it.annotations.hasAnnotation(BINDS_FQ_NAME) }
            .map {
                it.validateTypeEqualityBinding()
                Binding(
                    Key(it.returnType!!, it.qualifiers()),
                    it.scopeAnnotations(),
                    Binding.Variation.Equality(it)
                )
            }

    private fun FunctionDescriptor.validateTypeEqualityBinding() {
        if (modality != Modality.ABSTRACT) {
            report(context.trace) { BINDS_METHOD_NOT_ABSTRACT.on(it) }
        }

        if (valueParameters.size != 1) {
            report(context.trace) { BINDS_METHOD_NOT_ONE_PARAMETER.on(it) }
            return
        }

        val paramType = valueParameters.first().type
        if (!paramType.isSubtypeOf(returnType!!)) {
            report(context.trace) { BINDS_TYPE_IS_NOT_ASSIGNABLE.on(it) }
            return
        }
    }
}

private val PROVIDES_FQ_NAME = FqName("dagger.Provides")
private val BINDS_FQ_NAME = FqName("dagger.Binds")
private val DAGGER_MODULE_FQ_NAME = FqName("dagger.Module")
