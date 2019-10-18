package me.shika.di.dagger.resolver.creator

import me.shika.di.FACTORY_DEPENDENCIES_NOT_DECLARED
import me.shika.di.FACTORY_DEPENDENCIES_NOT_PROVIDED
import me.shika.di.FACTORY_MODULE_NOT_PROVIDED
import me.shika.di.FACTORY_WRONG_METHOD
import me.shika.di.dagger.resolver.ComponentAnnotationDescriptor
import me.shika.di.dagger.resolver.DAGGER_BINDS_INSTANCE_FQ_NAME
import me.shika.di.dagger.resolver.ResolverContext
import me.shika.di.dagger.resolver.allDescriptors
import me.shika.di.dagger.resolver.report
import me.shika.di.model.Binding
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.FUNCTIONS
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class DaggerFactoryDescriptor(
    private val component: ClassDescriptor,
    private val definition: ClassDescriptor,
    private val componentAnnotation: ComponentAnnotationDescriptor,
    private val context: ResolverContext
) : CreatorDescriptor {
    var method: FunctionDescriptor? = null
        private set

    override var instances: List<Binding> = emptyList()
        private set

    init {
        parseDefinition()
    }

    private fun parseDefinition() {
        val factoryMethods = definition.allDescriptors(FUNCTIONS)
            .filterIsInstance<FunctionDescriptor>()
            .filter { it.modality == Modality.ABSTRACT }

        if (factoryMethods.size != 1 || factoryMethods.none { component.defaultType.isSubtypeOf(it.returnType!!) }) {
            definition.report(context.trace) { FACTORY_WRONG_METHOD.on(it, factoryMethods) }
            return
        }

        method = factoryMethods.first()

        val instanceParams = method!!.valueParameters.filter { it.annotations.hasAnnotation(DAGGER_BINDS_INSTANCE_FQ_NAME) }
        val dependencyParams = method!!.valueParameters.asSequence().filterNot { it in instanceParams }
        checkFactoryParams(dependencyParams)

        instances = instanceParams.map { it.toInstanceBinding() }
    }

    private fun checkFactoryParams(dependencyParams: Sequence<ValueParameterDescriptor>) {
        val declaredDependencies = componentAnnotation.dependencies
        val declaredModuleInstances = componentAnnotation.moduleInstances

        val notProvidedDependencies = declaredDependencies.filterNot { it.defaultType in dependencyParams.map { it.type } }
        reportOnMethod(notProvidedDependencies.map { it.defaultType }, FACTORY_DEPENDENCIES_NOT_PROVIDED)

        val notProvidedModules = declaredModuleInstances.filterNot { it.defaultType in dependencyParams.map { it.type } }
        reportOnMethod(notProvidedModules.map { it.defaultType }, FACTORY_MODULE_NOT_PROVIDED)

        val notDeclared = dependencyParams.filterNot { param ->
            declaredDependencies.any { param.type == it.defaultType } || declaredModuleInstances.any { param.type == it.defaultType }
        }
        reportNotDeclared(notDeclared.toList())
    }

    private fun reportNotDeclared(list: List<ValueParameterDescriptor>) {
        list.forEach {
            it.report(context.trace) { FACTORY_DEPENDENCIES_NOT_DECLARED.on(it) }
        }
    }

    private fun reportOnMethod(list: List<KotlinType>, diagnosticFactory: DiagnosticFactory1<PsiElement, KotlinType>) {
        list.forEach { type ->
            method?.report(context.trace) { diagnosticFactory.on(it, type) }
        }
    }
}
